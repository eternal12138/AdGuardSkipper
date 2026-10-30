package com.adguardskipper.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.adguardskipper.data.model.LocalSkipRule
import com.adguardskipper.util.AccessibilityNodeUtils
import com.adguardskipper.util.UiActions
import kotlinx.coroutines.delay

/**
 * Conservative skip engine.
 *
 * Splash phase (first four seconds after a launcher cold start):
 *  - user custom rules bound to the current Activity
 *  - strict splash text: 跳过 / 跳过广告 / 跳过5s / 5s跳过
 *  - ad-SDK ViewId containing both an ad prefix and skip/jump
 *  - only small buttons in a screen corner
 *
 * Daily use:
 *  - only user rules created from the visual inspector, bound to the Activity
 *
 * There is deliberately no geometry-only scan, no bare-number countdown scan,
 * no generic close text and no generic close ViewId matching.
 */
class SkipEngine(
    private val service: AccessibilityService,
) {
    @Volatile
    private var localRules: Map<String, LocalSkipRule> = emptyMap()

    @Volatile
    private var lastTapAt = 0L

    private var lastClickedRuleKey: String? = null
    private var lastClickedRuleAt = 0L
    private var currentPageKey: String? = null
    private val pageRuleClicks = mutableSetOf<String>()

    fun clearLocalRules() {
        localRules = emptyMap()
        currentPageKey = null
        pageRuleClicks.clear()
        lastClickedRuleKey = null
        lastClickedRuleAt = 0L
    }

    fun updateLocalRules(rules: Map<String, LocalSkipRule>) {
        localRules = rules.mapValues { (_, rule) ->
            rule.copy(
                viewIds = rule.viewIds
                    .mapNotNull { item ->
                        normalizeViewId(item.viewId)
                            .takeIf { it.isNotEmpty() }
                            ?.let { item.copy(viewId = it) }
                    }
                    .distinctBy { it.viewId },
                coordinates = rule.coordinates
                    .filter { it.point.x in 0f..1f && it.point.y in 0f..1f }
                    .distinctBy { "${it.point.x}:${it.point.y}:${it.activityName}" },
                texts = rule.texts
                    .mapNotNull { item ->
                        compactText(item.text)
                            .takeIf { it.isNotEmpty() }
                            ?.let { item.copy(text = it) }
                    }
                    .distinctBy { "${it.text.lowercase()}:${it.activityName}" },
            )
        }
    }

    /**
     * Strict splash-phase scan. [isSplashPhase] must only be true for the first
     * 4000 ms after entering a new app from a launcher.
     */
    suspend fun scan(
        packageName: String,
        sessionStartedAt: Long,
        now: Long,
        roots: List<AccessibilityNodeInfo>,
        isSplashPhase: Boolean,
        pageSessionKey: String,
    ): SkipResult? {
        if (!isSplashPhase) return null
        if (!isScannablePackage(packageName)) return null
        if (isAutomationPaused()) return null
        val elapsed = now - sessionStartedAt
        if (elapsed !in 0..SPLASH_PHASE_MS) return null
        if (now - lastTapAt < TAP_COOLDOWN_MS) return null

        val safeRoots = roots.filterNot(::isOwnWindowRoot)
        if (safeRoots.isEmpty()) return null
        val nodes = collectNodes(safeRoots)
        if (nodes.isEmpty()) return null

        val screen = service.resources.displayMetrics
        val screenWidth = screen.widthPixels.coerceAtLeast(1)
        val screenHeight = screen.heightPixels.coerceAtLeast(1)

        val candidates = buildSplashCandidates(
            packageName = packageName,
            nodes = nodes,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
        )

        // Splash phase is intentionally limited to strict text and strict SDK
        // IDs. User rules and coordinates are event-driven daily rules only.
        return tryCandidates(candidates, pageSessionKey)
    }

    /**
     * Daily/event-driven scan. Only explicit user-created rules are allowed.
     * No generic text, ViewId, geometry or SDK heuristics are evaluated.
     */
    suspend fun scanInAppPopup(
        packageName: String,
        activityClassName: String,
        pageSessionKey: String,
        roots: List<AccessibilityNodeInfo>,
        eventIsPopup: Boolean,
        now: Long = SystemClock.elapsedRealtime(),
    ): SkipResult? {
        if (!isScannablePackage(packageName)) return null
        if (isAutomationPaused()) return null
        if (now - lastTapAt < TAP_COOLDOWN_MS) return null

        val safeRoots = roots.filterNot(::isOwnWindowRoot)
        if (safeRoots.isEmpty()) return null
        val nodes = collectNodes(safeRoots)
        if (nodes.isEmpty()) return null

        val localRule = localRules[packageName] ?: LocalSkipRule()
        val candidates = buildCustomCandidates(
            packageName = packageName,
            nodes = nodes,
            localRule = localRule,
            activityClassName = activityClassName,
        )
        tryCandidates(candidates, pageSessionKey)?.let { return it }

        // Coordinates are the riskiest rule type. Only an actual popup/dialog
        // window event or a dialog-class node may authorize them.
        val popupContext = eventIsPopup || nodes.any { node ->
            isPopupClassName(
                runCatching { node.className?.toString().orEmpty() }.getOrDefault(""),
            )
        }
        if (!popupContext) return null
        val screen = service.resources.displayMetrics
        return tryCustomCoordinates(
            packageName = packageName,
            localRule = localRule,
            activityClassName = activityClassName,
            pageSessionKey = pageSessionKey,
            screenWidth = screen.widthPixels.coerceAtLeast(1),
            screenHeight = screen.heightPixels.coerceAtLeast(1),
        )
    }

    private fun buildSplashCandidates(
        packageName: String,
        nodes: List<AccessibilityNodeInfo>,
        screenWidth: Int,
        screenHeight: Int,
    ): List<Candidate> {
        val candidates = mutableListOf<Candidate>()

        nodes.forEach { node ->
            val viewId = AccessibilityNodeUtils.normalizedViewId(node)
            val bounds = AccessibilityNodeUtils.boundsOf(node) ?: return@forEach
            val area = bounds.width().toLong() * bounds.height().toLong()
            val texts = nodeTexts(node)

            if (isStrictSdkSkipId(viewId) &&
                isStrictSplashButton(bounds, screenWidth, screenHeight)
            ) {
                candidates += Candidate(
                    node = node,
                    priority = PRIORITY_SDK_VIEW_ID,
                    area = area,
                    trigger = "严格广告 SDK ViewId",
                    detail = viewId,
                    ruleKey = "$packageName|sdk:$viewId",
                )
            }

            val strictText = texts.firstOrNull { SPLASH_TEXT_REGEX.matches(it) }
            if (strictText != null &&
                isStrictSplashButton(bounds, screenWidth, screenHeight)
            ) {
                candidates += Candidate(
                    node = node,
                    priority = PRIORITY_STRICT_TEXT,
                    area = area,
                    trigger = "严格开屏文本",
                    detail = strictText,
                    ruleKey = "$packageName|text:$strictText",
                )
            }
        }
        return candidates.sortedWith(compareBy<Candidate> { it.priority }.thenBy { it.area })
    }

    private fun buildCustomCandidates(
        packageName: String,
        nodes: List<AccessibilityNodeInfo>,
        localRule: LocalSkipRule,
        activityClassName: String,
    ): List<Candidate> {
        val candidates = mutableListOf<Candidate>()
        val idRules = localRule.viewIds.filter { rule ->
            rule.activityName.isNotBlank() &&
                activityMatches(activityClassName, rule.activityName)
        }
        val textRules = localRule.texts.filter { rule ->
            rule.activityName.isNotBlank() &&
                activityMatches(activityClassName, rule.activityName)
        }
        if (idRules.isEmpty() && textRules.isEmpty()) return emptyList()

        nodes.forEach { node ->
            val viewId = AccessibilityNodeUtils.normalizedViewId(node)
            val bounds = AccessibilityNodeUtils.boundsOf(node) ?: return@forEach
            val area = bounds.width().toLong() * bounds.height().toLong()
            val texts = nodeTexts(node)

            idRules.firstOrNull { it.viewId == viewId }?.let { rule ->
                candidates += Candidate(
                    node = node,
                    priority = PRIORITY_CUSTOM_ID,
                    area = area,
                    trigger = "自定义 ViewId 规则",
                    detail = rule.viewId,
                    ruleKey = "$packageName|id:${rule.viewId}|${rule.activityName}",
                )
            }

            textRules.firstOrNull { rule -> localTextMatches(texts, rule.text) }?.let { rule ->
                candidates += Candidate(
                    node = node,
                    priority = PRIORITY_CUSTOM_TEXT,
                    area = area,
                    trigger = "自定义文本规则",
                    detail = rule.text,
                    ruleKey = "$packageName|text:${rule.text}|${rule.activityName}",
                )
            }
        }
        return candidates.sortedWith(compareBy<Candidate> { it.priority }.thenBy { it.area })
    }

    private suspend fun tryCandidates(
        candidates: List<Candidate>,
        pageSessionKey: String,
    ): SkipResult? {
        for (candidate in candidates) {
            if (isAutomationPaused()) return null
            if (SystemClock.elapsedRealtime() - lastTapAt < TAP_COOLDOWN_MS) return null
            if (!canClickRule(candidate.ruleKey, pageSessionKey, SystemClock.elapsedRealtime())) {
                continue
            }
            executeCandidate(candidate, pageSessionKey)?.let { return it }
        }
        return null
    }

    private suspend fun executeCandidate(
        candidate: Candidate,
        pageSessionKey: String,
    ): SkipResult? {
        if (isAutomationPaused()) return null
        if (candidate.node.packageName?.toString() == service.packageName) return null
        val now = SystemClock.elapsedRealtime()
        if (!canClickRule(candidate.ruleKey, pageSessionKey, now)) return null
        markRuleClicked(candidate.ruleKey, pageSessionKey, now)

        val node = candidate.node
        val center = AccessibilityNodeUtils.centerOf(node)
        val gestureAccepted = center != null && UiActions.tap(service, center.x, center.y)
        if (isAutomationPaused()) return null

        val nodeClickAccepted = if (runCatching { node.isClickable }.getOrDefault(false)) {
            runCatching { node.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                .getOrDefault(false)
        } else {
            false
        }

        var parent = runCatching { node.parent }.getOrNull()
        var guard = 0
        var parentClickAccepted = false
        while (guard < 4) {
            val currentParent = parent ?: break
            if (runCatching { currentParent.isClickable }.getOrDefault(false)) {
                parentClickAccepted = runCatching {
                    currentParent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }.getOrDefault(false)
                break
            }
            parent = runCatching { currentParent.parent }.getOrNull()
            guard++
        }

        lastTapAt = SystemClock.elapsedRealtime()
        if (!gestureAccepted && !nodeClickAccepted && !parentClickAccepted) return null
        delay(180)
        return SkipResult(
            trigger = candidate.trigger,
            detail = candidate.detail,
        )
    }

    private suspend fun tryCustomCoordinates(
        packageName: String,
        localRule: LocalSkipRule,
        activityClassName: String,
        pageSessionKey: String,
        screenWidth: Int,
        screenHeight: Int,
    ): SkipResult? {
        if (activityClassName.isBlank()) return null
        val coordinate = localRule.coordinates.firstOrNull { rule ->
            rule.activityName.isNotBlank() &&
                activityMatches(activityClassName, rule.activityName) &&
                canClickRule(
                    "$packageName|coord:${rule.point.x}:${rule.point.y}|${rule.activityName}",
                    pageSessionKey,
                    SystemClock.elapsedRealtime(),
                )
        } ?: return null

        val now = SystemClock.elapsedRealtime()
        if (now - lastTapAt < TAP_COOLDOWN_MS) return null
        val ruleKey = "$packageName|coord:${coordinate.point.x}:${coordinate.point.y}|" +
            coordinate.activityName
        markRuleClicked(ruleKey, pageSessionKey, now)

        val x = screenWidth * coordinate.point.x
        val y = screenHeight * coordinate.point.y
        if (!UiActions.tap(service, x, y)) return null
        lastTapAt = SystemClock.elapsedRealtime()
        return SkipResult(
            trigger = "自定义坐标规则",
            detail = "$activityClassName · ${(coordinate.point.x * 100).toInt()}%," +
                "${(coordinate.point.y * 100).toInt()}%",
        )
    }

    private fun canClickRule(
        ruleKey: String,
        pageSessionKey: String,
        now: Long,
    ): Boolean {
        if (currentPageKey != pageSessionKey) {
            currentPageKey = pageSessionKey
            pageRuleClicks.clear()
        }
        if (ruleKey in pageRuleClicks) return false
        if (ruleKey == lastClickedRuleKey && now - lastClickedRuleAt < RULE_REPEAT_WINDOW_MS) {
            return false
        }
        return true
    }

    private fun markRuleClicked(
        ruleKey: String,
        pageSessionKey: String,
        now: Long,
    ) {
        if (currentPageKey != pageSessionKey) {
            currentPageKey = pageSessionKey
            pageRuleClicks.clear()
        }
        pageRuleClicks += ruleKey
        lastClickedRuleKey = ruleKey
        lastClickedRuleAt = now
    }

    private fun collectNodes(roots: List<AccessibilityNodeInfo>): List<AccessibilityNodeInfo> {
        val result = linkedMapOf<String, AccessibilityNodeInfo>()
        for (root in roots) {
            if (isOwnWindowRoot(root)) continue
            if (result.size >= MAX_TOTAL_NODES) break
            val flattened = AccessibilityNodeUtils.flatten(root, limit = MAX_NODES_PER_WINDOW)
            for (node in flattened) {
                if (result.size >= MAX_TOTAL_NODES) break
                if (node.packageName?.toString() == service.packageName) continue
                val key = nodeKey(node)
                if (!result.containsKey(key)) {
                    result[key] = node
                }
            }
        }
        return result.values.toList()
    }

    private fun nodeTexts(node: AccessibilityNodeInfo): List<String> {
        val result = mutableListOf<String>()
        runCatching { node.text?.toString() }.getOrNull()
            ?.let(::compactText)
            ?.takeIf { it.isNotEmpty() }
            ?.let(result::add)
        runCatching { node.contentDescription?.toString() }.getOrNull()
            ?.let(::compactText)
            ?.takeIf { it.isNotEmpty() }
            ?.let(result::add)
        return result.distinct()
    }

    private fun localTextMatches(nodeTexts: List<String>, wanted: String): Boolean {
        if (wanted.isBlank()) return false
        val escaped = Regex.escape(wanted)
        val withCountdown = Regex(
            "^($escaped[0-9]+[sS秒]?|[0-9]+[sS秒]?$escaped)$",
            RegexOption.IGNORE_CASE,
        )
        return nodeTexts.any { text ->
            text.equals(wanted, ignoreCase = true) ||
                withCountdown.matches(text)
        }
    }

    private fun isStrictSdkSkipId(viewId: String): Boolean {
        if (viewId.isEmpty()) return false
        val hasAdPrefix = AD_SDK_ID_PREFIXES.any { viewId.contains(it) }
        val hasSkipWord = SDK_SKIP_WORDS.any { viewId.contains(it) }
        return hasAdPrefix && hasSkipWord
    }

    private fun isStrictSplashButton(
        bounds: Rect,
        screenWidth: Int,
        screenHeight: Int,
    ): Boolean {
        val width = bounds.width()
        val height = bounds.height()
        if (width <= 0 || height <= 0) return false
        if (width >= screenWidth * 0.30f || height >= screenHeight * 0.10f) return false

        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()
        val horizontalEdge = centerX <= screenWidth * 0.35f || centerX >= screenWidth * 0.65f
        val verticalEdge = centerY <= screenHeight * 0.25f || centerY >= screenHeight * 0.75f
        return horizontalEdge && verticalEdge
    }

    private fun activityMatches(current: String, recorded: String): Boolean {
        if (current.isBlank() || recorded.isBlank()) return false
        if (current == recorded) return true
        return current.substringAfterLast('.') == recorded.substringAfterLast('.')
    }

    private fun isPopupClassName(className: String): Boolean {
        if (className.isBlank()) return false
        val simple = className.substringAfterLast('.').lowercase()
        return simple.contains("dialog") ||
            simple.contains("popup") ||
            simple.contains("sheet") ||
            simple.contains("modal") ||
            simple.contains("overlay")
    }

    private fun isScannablePackage(packageName: String): Boolean {
        if (packageName.isBlank() || packageName == service.packageName) return false
        if (packageName in PROTECTED_PACKAGES) return false
        val lower = packageName.lowercase()
        if (lower.contains("packageinstaller") ||
            lower.contains("permissioncontroller") ||
            lower.endsWith(".settings") ||
            lower.contains("inputmethod") ||
            lower.endsWith(".ime")
        ) {
            return false
        }
        return true
    }

    private fun isAutomationPaused(): Boolean =
        (service as? AdSkipAccessibilityService)?.isAutomationPaused() == true

    private fun isOwnWindowRoot(root: AccessibilityNodeInfo): Boolean =
        root.packageName?.toString() == service.packageName

    private fun nodeKey(node: AccessibilityNodeInfo): String {
        val bounds = AccessibilityNodeUtils.boundsOf(node)
        val boundsKey = bounds?.let {
            "${it.left},${it.top},${it.right},${it.bottom}"
        }.orEmpty()
        return "${node.windowId}:${node.className}:${node.viewIdResourceName}:" +
            "${node.text}:${node.contentDescription}:$boundsKey"
    }

    private fun normalizeViewId(raw: String): String =
        raw.trim().substringAfterLast(':').substringAfterLast('/').lowercase()

    private fun compactText(raw: String): String =
        raw.replace(Regex("[\\s\u200B-\u200D\uFEFF]"), "")

    private data class Candidate(
        val node: AccessibilityNodeInfo,
        val priority: Int,
        val area: Long,
        val trigger: String,
        val detail: String,
        val ruleKey: String,
    )

    data class SkipResult(
        val trigger: String,
        val detail: String,
    )

    companion object {
        const val SPLASH_PHASE_MS = 4_000L
        const val RULE_REPEAT_WINDOW_MS = 5_000L
        private const val TAP_COOLDOWN_MS = 800L
        private const val MAX_TOTAL_NODES = 1_200
        private const val MAX_NODES_PER_WINDOW = 700

        private const val PRIORITY_CUSTOM_ID = 0
        private const val PRIORITY_CUSTOM_TEXT = 1
        private const val PRIORITY_SDK_VIEW_ID = 5
        private const val PRIORITY_STRICT_TEXT = 10

        private val SPLASH_TEXT_REGEX = Regex(
            "^(跳过|跳过广告|跳过[0-9]+[sS秒]?|[0-9]+[sS秒]?跳过)$",
        )

        private val AD_SDK_ID_PREFIXES = listOf(
            "tt_",
            "ksad_",
            "gdt_",
            "beizi_",
            "ad_",
            "splash_",
        )
        private val SDK_SKIP_WORDS = listOf("skip", "jump")

        private val PROTECTED_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.inputmethod.latin",
            "com.google.android.inputmethod.latin",
            "com.sohu.inputmethod.sogou",
            "com.iflytek.inputmethod",
            "com.baidu.input",
        )
    }
}

