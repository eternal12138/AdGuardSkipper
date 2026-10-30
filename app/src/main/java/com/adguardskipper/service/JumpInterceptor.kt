package com.adguardskipper.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.adguardskipper.data.AppRepository
import com.adguardskipper.data.model.InterceptLogType
import com.adguardskipper.util.AccessibilityNodeUtils
import com.adguardskipper.util.UiActions

/**
 * Two independent defenses:
 *
 * 1. Reject system/app dialogs that ask to open a third-party app.
 * 2. When the user has just launched app A from the launcher, remember the
 *    source package for 3.5 s. If a blacklisted app is suddenly brought to the
 *    foreground without the launcher being seen in between, press BACK to
 *    return to A and report the interception.
 */
class JumpInterceptor(
    private val service: AccessibilityService,
    private val appRepository: AppRepository,
) {
    var onIntercept: ((InterceptEvent) -> Unit)? = null

    @Volatile
    private var launcherPackages: Set<String> = emptySet()

    @Volatile
    private var blacklist: Set<String> = emptySet()

    private var currentForeground: String? = null
    private var protectedSource: String? = null
    private var protectionStartedAt = 0L
    private var armedFromLauncher = false
    private var lastInterceptAt = 0L
    private var lastPopupDismissAt = 0L

    fun updateLauncherPackages(packages: Set<String>) {
        launcherPackages = packages.toSet()
    }

    fun updateBlacklist(packages: Set<String>) {
        blacklist = packages.toSet()
    }

    /** Clears all transient launch/intercept state after consent is revoked. */
    fun reset() {
        currentForeground = null
        protectedSource = null
        protectionStartedAt = 0L
        armedFromLauncher = false
        lastInterceptAt = 0L
        lastPopupDismissAt = 0L
    }

    /**
     * Called for every TYPE_WINDOW_STATE_CHANGED event. [now] must use
     * SystemClock.elapsedRealtime().
     */
    fun onForegroundPackage(
        packageName: String,
        now: Long = SystemClock.elapsedRealtime(),
    ): ForegroundDecision {
        if (packageName.isBlank()) return ForegroundDecision()
        if (packageName == service.packageName) return ForegroundDecision()
        if (packageName in SYSTEM_UI_PACKAGES) return ForegroundDecision()

        if (packageName in launcherPackages) {
            currentForeground = packageName
            armedFromLauncher = true
            protectedSource = null
            protectionStartedAt = 0L
            return ForegroundDecision()
        }

        if (packageName == currentForeground) return ForegroundDecision()
        currentForeground = packageName

        if (armedFromLauncher && protectedSource == null) {
            // The first non-launcher app after HOME is the splash source.
            armedFromLauncher = false
            protectedSource = packageName
            protectionStartedAt = now
            return ForegroundDecision()
        }

        val source = protectedSource
        if (source != null && source != packageName) {
            val withinWindow = now - protectionStartedAt in 0..PROTECTION_WINDOW_MS
            val targetIsBlacklisted = packageName in blacklist
            val cooldownFinished = now - lastInterceptAt >= INTERCEPT_COOLDOWN_MS

            if (withinWindow && targetIsBlacklisted && cooldownFinished) {
                lastInterceptAt = now
                val elapsedInWindow = (now - protectionStartedAt).coerceAtLeast(0L)
                // Clear the state before BACK so the return-to-source event
                // cannot trigger a second interception loop.
                protectedSource = null
                protectionStartedAt = 0L
                armedFromLauncher = false

                val backHandled = service.performGlobalAction(
                    AccessibilityService.GLOBAL_ACTION_BACK,
                )
                onIntercept?.invoke(
                    InterceptEvent(
                        type = InterceptLogType.BLOCK_JUMP,
                        packageName = packageName,
                        sourcePackage = source,
                        trigger = "开屏保护期强跳拦截",
                        detail = buildString {
                            append("从 ")
                            append(appRepository.labelForPackage(source))
                            append(" 跳转至 ")
                            append(appRepository.labelForPackage(packageName))
                            append("，")
                            append(if (backHandled) "已执行返回" else "返回动作未能执行")
                            append("（")
                            append(elapsedInWindow)
                            append("ms 内）")
                        },
                    ),
                )
                return ForegroundDecision(intercepted = true, sourcePackage = source)
            }
        }

        if (protectedSource != null && now - protectionStartedAt > PROTECTION_WINDOW_MS) {
            protectedSource = null
            protectionStartedAt = 0L
        }
        return ForegroundDecision()
    }

    data class ForegroundDecision(
        val intercepted: Boolean = false,
        val sourcePackage: String? = null,
    )

    /**
     * Inspects all interactive windows for a dialog that both offers to open an
     * external app and exposes a reject/cancel action. Returns true when a
     * rejection was dispatched.
     */
    suspend fun dismissThirdPartyDialog(
        roots: List<AccessibilityNodeInfo>,
        foregroundPackage: String,
        now: Long = SystemClock.elapsedRealtime(),
    ): Boolean {
        if (roots.isEmpty()) return false
        if (now - lastPopupDismissAt < POPUP_COOLDOWN_MS) return false

        val windows = roots.mapNotNull { root ->
            val nodes = AccessibilityNodeUtils.flatten(root, limit = 400)
            if (nodes.isEmpty()) return@mapNotNull null
            WindowScan(
                packageName = runCatching { root.packageName?.toString().orEmpty() }
                    .getOrDefault(""),
                nodes = nodes,
                text = nodes.joinToString(" ") { AccessibilityNodeUtils.textOf(it) },
            )
        }
        if (windows.isEmpty()) return false

        // Require the positive/negative buttons and the external-open context to
        // be present in the same window. This avoids denying an unrelated system
        // permission prompt just because the page underneath contains "打开".
        val dialog = windows.firstOrNull { window ->
            val hasPositive = POSITIVE_WORDS.any { word ->
                window.text.contains(word, ignoreCase = true)
            }
            val hasNegative = NEGATIVE_WORDS.any { word ->
                window.text.contains(word, ignoreCase = true)
            }
            val hasContext = CONTEXT_MARKERS.any { marker ->
                window.text.contains(marker, ignoreCase = true)
            } || (
                window.packageName in SYSTEM_DIALOG_PACKAGES &&
                    window.text.contains("打开", ignoreCase = true)
                )
            hasPositive && hasNegative && hasContext
        } ?: return false

        val contextMarker = CONTEXT_MARKERS.firstOrNull { marker ->
            dialog.text.contains(marker, ignoreCase = true)
        } ?: if (dialog.packageName in SYSTEM_DIALOG_PACKAGES) "系统打开应用" else null

        val negativeCandidates = dialog.nodes.mapNotNull { node ->
            val text = AccessibilityNodeUtils.textOf(node)
            val negativeWord = NEGATIVE_WORDS.firstOrNull { word ->
                text.contains(word, ignoreCase = true)
            } ?: return@mapNotNull null
            val hasClickTarget = runCatching { node.isClickable }.getOrDefault(false) ||
                AccessibilityNodeUtils.findClickableAncestor(node) != null
            NegativeCandidate(
                node = node,
                text = text,
                word = negativeWord,
                actionable = hasClickTarget,
            )
        }

        val bestNegative = negativeCandidates.minWithOrNull(
            compareBy<NegativeCandidate> { if (it.actionable) 0 else 1 }
                .thenBy { it.text.length },
        ) ?: return false

        lastPopupDismissAt = now
        val dismissed = UiActions.clickOrGesture(service, bestNegative.node)
        if (!dismissed) return false

        val positiveWord = POSITIVE_WORDS.firstOrNull { word ->
            dialog.text.contains(word, ignoreCase = true)
        }.orEmpty()
        onIntercept?.invoke(
            InterceptEvent(
                type = InterceptLogType.BLOCK_POPUP,
                packageName = foregroundPackage,
                sourcePackage = null,
                trigger = "第三方应用弹窗拦截",
                detail = buildString {
                    append("检测到“")
                    append(contextMarker ?: "系统打开应用")
                    append("”，自动点击“")
                    append(bestNegative.word)
                    append("”")
                    if (positiveWord.isNotBlank()) {
                        append("，拒绝“")
                        append(positiveWord)
                        append("”")
                    }
                },
            ),
        )
        return true
    }

    data class InterceptEvent(
        val type: InterceptLogType,
        val packageName: String,
        val sourcePackage: String?,
        val trigger: String,
        val detail: String,
    )

    private data class WindowScan(
        val packageName: String,
        val nodes: List<AccessibilityNodeInfo>,
        val text: String,
    )

    private data class NegativeCandidate(
        val node: AccessibilityNodeInfo,
        val text: String,
        val word: String,
        val actionable: Boolean,
    )

    companion object {
        const val PROTECTION_WINDOW_MS = 3_500L
        private const val INTERCEPT_COOLDOWN_MS = 1_500L
        private const val POPUP_COOLDOWN_MS = 1_200L

        private val SYSTEM_UI_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.internal.systemui.navbar.gestural",
            "com.android.internal.systemui.navbar.threebutton",
        )

        private val SYSTEM_DIALOG_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.android.packageinstaller",
        )

        private val CONTEXT_MARKERS = listOf(
            "打开第三方应用",
            "打开其他应用",
            "打开外部应用",
            "即将离开",
            "离开当前应用",
            "离开",
            "跳转到第三方",
            "跳转至第三方",
            "打开应用",
        )

        private val POSITIVE_WORDS = listOf("允许", "打开", "继续", "确定", "前往")

        private val NEGATIVE_WORDS = listOf("取消", "拒绝", "忽略", "不允许", "暂不", "禁止")
    }
}
