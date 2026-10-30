package com.adguardskipper.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.adguardskipper.AdGuardSkipperApp
import com.adguardskipper.data.AppPreferences
import com.adguardskipper.data.model.AppSettings
import com.adguardskipper.data.model.CapturedNode
import com.adguardskipper.data.model.InterceptLog
import com.adguardskipper.data.model.InterceptLogType
import com.adguardskipper.data.model.LayoutSnapshot
import com.adguardskipper.data.model.NodeBounds
import com.adguardskipper.util.AccessibilityNodeUtils
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Accessibility entry point. It owns the five-second splash session and routes
 * work to the two focused engines:
 *
 *  - [SkipEngine] for finding and tapping splash skip controls
 *  - [JumpInterceptor] for rejecting external-app dialogs and pulling back
 *    unauthorized jumps within the 3.5 s launch protection window
 *
 * It also exposes a layout snapshot API for the floating inspector and hosts the
 * persistent [KeepAliveHelper] foreground notification.
 */
class AdSkipAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var skipEngine: SkipEngine
    private lateinit var jumpInterceptor: JumpInterceptor
    private lateinit var app: AdGuardSkipperApp
    private lateinit var appPreferences: AppPreferences

    private var setupJob: Job? = null
    private var settingsJob: Job? = null
    private var customRulesJob: Job? = null
    private var scanJob: Job? = null
    private var inAppPopupJob: Job? = null
    private var keepAliveJob: Job? = null
    private var keepAliveStarted = false

    @Volatile
    private var launcherPackages: Set<String> = emptySet()

    @Volatile
    private var latestSettings: AppSettings = AppSettings()

    @Volatile
    private var runtimeReady = false

    private var currentForegroundPackage: String? = null
    private var currentActivityClassName: String = ""
    private var pendingForegroundPackage: String? = null
    private var pendingActivityClassName: String = ""
    private var activeSession: ScanSession? = null
    private var lastInAppPopupScanAt = 0L
    private var foregroundStartedAt = 0L
    private var pageSessionStartedAt = 0L
    private var appWentAway = false
    private var inspectorDialogShowing = false
    private var agreementWasAccepted = false

    override fun onCreate() {
        super.onCreate()
        app = application as AdGuardSkipperApp
        appPreferences = app.container.preferences
        skipEngine = SkipEngine(this)
        jumpInterceptor = JumpInterceptor(this, app.container.appRepository)
        jumpInterceptor.onIntercept = ::handleInterceptEvent
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        runningInstance = this
        runtimeReady = false
        pendingForegroundPackage = null
        pendingActivityClassName = ""
        currentActivityClassName = ""
        foregroundStartedAt = SystemClock.elapsedRealtime()
        pageSessionStartedAt = foregroundStartedAt
        appWentAway = false
        inspectorDialogShowing = false
        stopScanSession()
        setupJob?.cancel()
        settingsJob?.cancel()
        customRulesJob?.cancel()
        inAppPopupJob?.cancel()

        keepAliveStarted = KeepAliveHelper.startGuard(this, latestSettings.blockCount)
        startKeepAliveLoop()
        agreementWasAccepted = appPreferences.isUserAgreementAccepted
        if (agreementWasAccepted) {
            app.container.layoutInspector.restoreIfEnabled()
        } else {
            app.container.layoutInspector.detachForServiceStop()
        }

        settingsJob = serviceScope.launch {
            appPreferences.settings.collect { settings ->
                latestSettings = settings
                val accepted = settings.isUserAgreementAccepted
                if (!accepted) {
                    // Consent can be revoked from the About page at any time.
                    // Drop every scan/click path immediately, not just new events.
                    stopScanSession()
                    inAppPopupJob?.cancel()
                    jumpInterceptor.reset()
                }
                if (accepted != agreementWasAccepted) {
                    if (accepted) {
                        app.container.layoutInspector.restoreIfEnabled()
                    } else {
                        app.container.layoutInspector.hide()
                    }
                    agreementWasAccepted = accepted
                }
                jumpInterceptor.updateBlacklist(settings.blacklist)
                if (keepAliveStarted) {
                    KeepAliveHelper.updateGuardNotification(
                        this@AdSkipAccessibilityService,
                        settings.blockCount,
                    )
                }
                val session = activeSession
                if (session != null && session.packageName in settings.whitelist) {
                    stopScanSession()
                }
            }
        }

        customRulesJob = serviceScope.launch {
            app.container.preferences.customRules.collect { rules ->
                skipEngine.updateLocalRules(rules)
            }
        }

        setupJob = serviceScope.launch {
            runCatching {
                app.container.preferences.clearLegacyRulesOnce()
                app.container.preferences.ensureFreshCounters()
                latestSettings = app.container.preferences.currentSettings()
                jumpInterceptor.updateBlacklist(latestSettings.blacklist)

                val discoveredLaunchers = app.container.appRepository.loadLauncherPackages()
                launcherPackages = discoveredLaunchers
                jumpInterceptor.updateLauncherPackages(discoveredLaunchers)

                val initialPackage = if (appPreferences.isUserAgreementAccepted) {
                    rootInActiveWindow?.packageName?.toString().orEmpty()
                } else {
                    ""
                }
                if (initialPackage.isNotBlank() &&
                    !isSystemCriticalPackage(initialPackage)
                ) {
                    currentForegroundPackage = initialPackage
                    foregroundStartedAt = SystemClock.elapsedRealtime()
                    pageSessionStartedAt = foregroundStartedAt
                    jumpInterceptor.onForegroundPackage(initialPackage)
                }

                if (keepAliveStarted) {
                    KeepAliveHelper.updateGuardNotification(
                        this@AdSkipAccessibilityService,
                        latestSettings.blockCount,
                    )
                }
                KeepAliveHelper.requestNotificationListenerRebind(this@AdSkipAccessibilityService)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Log.e(TAG, "Failed to initialize accessibility runtime", error)
            }

            runtimeReady = true
            pendingForegroundPackage?.let { pending ->
                val pendingClass = pendingActivityClassName
                pendingForegroundPackage = null
                pendingActivityClassName = ""
                handleForegroundPackage(pending, pendingClass)
            }
        }
    }

    private fun startKeepAliveLoop() {
        keepAliveJob?.cancel()
        keepAliveJob = serviceScope.launch {
            while (isActive) {
                delay(KEEP_ALIVE_REFRESH_MS)
                reassertKeepAliveNotification()
                KeepAliveHelper.requestNotificationListenerRebind(this@AdSkipAccessibilityService)
            }
        }
    }

    /**
     * Called by the notification listener when it reconnects, and periodically by
     * this service. It restores the foreground notification if the system dropped
     * it while keeping the accessibility binding alive.
     */
    fun reassertKeepAliveNotification() {
        if (!keepAliveStarted) {
            keepAliveStarted = KeepAliveHelper.startGuard(this, latestSettings.blockCount)
        } else {
            KeepAliveHelper.updateGuardNotification(this, latestSettings.blockCount)
        }
    }

    /**
     * Captures visible nodes from all non-self windows for the layout inspector.
     * Self windows (floating ball, review panel) are filtered both at window and
     * node level. Must be invoked on the main thread.
     */
    fun dumpCurrentWindowNodes(): LayoutSnapshot? {
        if (!appPreferences.isUserAgreementAccepted) return null
        val allRoots = getAllWindowRoots().filterNot { isOwnWindowRoot(it) }
        if (allRoots.isEmpty()) return null

        val activeRoot = rootInActiveWindow?.takeUnless { isOwnWindowRoot(it) }
        val targetPackage = activeRoot?.packageName?.toString()
            ?.takeIf { it.isNotBlank() && it != this.packageName }
            ?: allRoots.firstNotNullOfOrNull { root ->
                root.packageName?.toString()?.takeIf {
                    it.isNotBlank() && it != this.packageName
                }
            }
            ?: return null
        val targetRoots = allRoots.filter { root ->
            root.packageName?.toString() == targetPackage
        }
        if (targetRoots.isEmpty()) return null

        val nodes = mutableListOf<CapturedNode>()
        val screen = resources.displayMetrics

        fun walk(node: android.view.accessibility.AccessibilityNodeInfo, depth: Int) {
            if (nodes.size >= MAX_CAPTURE_NODES) return
            val nodePackage = runCatching { node.packageName?.toString().orEmpty() }
                .getOrDefault("")
            if (nodePackage == this.packageName) return

            val visible = runCatching { node.isVisibleToUser }.getOrDefault(false)
            if (visible) {
                val bounds = AccessibilityNodeUtils.boundsOf(node)
                if (bounds != null) {
                    nodes += CapturedNode(
                        depth = depth,
                        className = runCatching { node.className?.toString().orEmpty() }
                            .getOrDefault(""),
                        text = runCatching { node.text?.toString().orEmpty() }
                            .getOrDefault(""),
                        contentDescription = runCatching {
                            node.contentDescription?.toString().orEmpty()
                        }.getOrDefault(""),
                        viewIdResourceName = runCatching {
                            node.viewIdResourceName.orEmpty()
                        }.getOrDefault(""),
                        clickable = runCatching { node.isClickable }.getOrDefault(false),
                        visible = true,
                        boundsInScreen = NodeBounds(
                            left = bounds.left,
                            top = bounds.top,
                            right = bounds.right,
                            bottom = bounds.bottom,
                        ),
                    )
                }
            }

            val childCount = runCatching { node.childCount }.getOrDefault(0)
            for (index in 0 until childCount) {
                if (nodes.size >= MAX_CAPTURE_NODES) break
                runCatching { node.getChild(index) }.getOrNull()?.let { child ->
                    walk(child, depth + 1)
                }
            }
        }

        targetRoots.forEach { root ->
            if (nodes.size >= MAX_CAPTURE_NODES) return@forEach
            walk(root, 0)
        }
        if (nodes.isEmpty()) return null
        return LayoutSnapshot(
            packageName = targetPackage,
            activityName = currentActivityClassName,
            capturedAt = System.currentTimeMillis(),
            screenWidth = screen.widthPixels,
            screenHeight = screen.heightPixels,
            truncated = nodes.size >= MAX_CAPTURE_NODES,
            nodes = nodes,
        )
    }

    fun captureCurrentLayout(): LayoutSnapshot? = dumpCurrentWindowNodes()

    /**
     * Set by LayoutInspectorFloatWindow. While the review panel is open, every
     * automatic click path is suspended.
     */
    fun onInspectorDialogVisibilityChanged(showing: Boolean) {
        inspectorDialogShowing = showing
        if (showing) {
            stopScanSession()
            inAppPopupJob?.cancel()
        } else {
            lastInAppPopupScanAt = 0L
        }
    }

    fun isAutomationPaused(): Boolean {
        return !appPreferences.isUserAgreementAccepted ||
            inspectorDialogShowing ||
            app.container.layoutInspector.isInspectorDialogShowing
    }

    /**
     * Invoked immediately when consent is revoked from the About page. The
     * DataStore flow also enforces this, but doing it synchronously guarantees
     * no click can slip through before the next flow emission.
     */
    fun onAgreementRevoked() {
        stopScanSession()
        inAppPopupJob?.cancel()
        lastInAppPopupScanAt = 0L
        currentForegroundPackage = null
        currentActivityClassName = ""
        pendingForegroundPackage = null
        pendingActivityClassName = ""
        foregroundStartedAt = 0L
        pageSessionStartedAt = 0L
        appWentAway = false
        jumpInterceptor.reset()
        app.container.layoutInspector.hide()
    }

    fun onCustomRulesCleared() {
        skipEngine.clearLocalRules()
        stopScanSession()
        inAppPopupJob?.cancel()
        lastInAppPopupScanAt = 0L
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Hard consent gate: before the user agreement is accepted this service
        // must not read nodes, click, or intercept anything, even if the user
        // force-enabled it from system settings.
        if (!appPreferences.isUserAgreementAccepted) {
            stopScanSession()
            inAppPopupJob?.cancel()
            return
        }
        if (event == null) return
        val packageName = event.packageName?.toString().orEmpty()
        if (packageName.isBlank()) return
        if (packageName == this.packageName) {
            // Never process our own Activity, floating ball or review overlay.
            currentForegroundPackage = packageName
            stopScanSession()
            inAppPopupJob?.cancel()
            return
        }
        if (isSystemCriticalPackage(packageName)) {
            // Settings, package installer, SystemUI, IMEs and every launcher are
            // absolutely exempt from scanning/clicking.
            handleProtectedForeground(packageName)
            return
        }
        if (isAutomationPaused()) return
        val eventClassName = event.className?.toString().orEmpty()
        val closeSignal = eventHasCloseSignal(event)

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleForegroundPackage(
                    packageName = packageName,
                    activityClassName = eventClassName,
                )
                requestInAppPopupScanIfEligible(
                    packageName = packageName,
                    eventClassName = eventClassName,
                )
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Content changes only trigger a custom-rule check when the
                // event itself carries a close hint. No generic click is made.
                if (closeSignal) {
                    requestInAppPopupScanIfEligible(
                        packageName = packageName,
                        eventClassName = eventClassName,
                    )
                }
            }

            else -> Unit
        }
    }

    private fun handleProtectedForeground(packageName: String) {
        if (packageName in launcherPackages) {
            // Launchers are needed by JumpInterceptor to arm desktop launches,
            // but they never participate in ad scanning.
            jumpInterceptor.onForegroundPackage(packageName)
            currentForegroundPackage = packageName
            currentActivityClassName = ""
            appWentAway = true
            pageSessionStartedAt = SystemClock.elapsedRealtime()
        }
        // Settings/SystemUI/installer/IME overlays must not disturb the current
        // app page identity; they only pause scanning.
        inAppPopupJob?.cancel()
        stopScanSession()
    }

    private fun handleForegroundPackage(
        packageName: String,
        activityClassName: String,
    ) {
        if (!appPreferences.isUserAgreementAccepted) {
            stopScanSession()
            inAppPopupJob?.cancel()
            return
        }
        if (packageName == this.packageName) {
            currentForegroundPackage = packageName
            stopScanSession()
            inAppPopupJob?.cancel()
            return
        }
        if (isAutomationPaused()) {
            stopScanSession()
            return
        }

        val now = SystemClock.elapsedRealtime()

        if (packageName in NON_APP_PACKAGES) {
            // Keep the last app Activity class so a same-Activity return from a
            // system overlay can still be recognised as a resume.
            appWentAway = true
            inAppPopupJob?.cancel()
            stopScanSession()
            return
        }
        if (!runtimeReady) {
            pendingForegroundPackage = packageName
            pendingActivityClassName = activityClassName
            return
        }
        if (packageName in launcherPackages) {
            jumpInterceptor.onForegroundPackage(packageName, now)
            currentForegroundPackage = packageName
            currentActivityClassName = ""
            appWentAway = true
            inAppPopupJob?.cancel()
            stopScanSession()
            return
        }

        if (packageName == currentForegroundPackage) {
            if (activityClassName.isNotBlank() && !isPopupLikeClassName(activityClassName)) {
                if (activityClassName != currentActivityClassName) {
                    currentActivityClassName = activityClassName
                    pageSessionStartedAt = now
                }
            }
            if (appWentAway) {
                appWentAway = false
                foregroundStartedAt = now
                pageSessionStartedAt = now
                inAppPopupJob?.cancel()
            }
            // No automatic session is restarted for a page transition. Daily
            // clicks are custom-rule only.
            return
        }

        val previousPackage = currentForegroundPackage
        val previousActivityClass = currentActivityClassName
        val fromLauncher = previousPackage != null && previousPackage in launcherPackages
        val returningFromOwnOverlay = previousPackage == this.packageName &&
            activityClassName.isNotBlank() &&
            activityClassName == previousActivityClass
        val decision = jumpInterceptor.onForegroundPackage(packageName, now)
        if (decision.intercepted) {
            // BACK was dispatched. Treat the expected return-to-source as the
            // current foreground, and never start a new splash scan for it.
            currentForegroundPackage = decision.sourcePackage
            currentActivityClassName = ""
            appWentAway = false
            pageSessionStartedAt = now
            inAppPopupJob?.cancel()
            stopScanSession()
            return
        }

        currentForegroundPackage = packageName
        currentActivityClassName = activityClassName
            .takeUnless(::isPopupLikeClassName)
            .orEmpty()
        appWentAway = false
        foregroundStartedAt = now
        if (!returningFromOwnOverlay) {
            pageSessionStartedAt = now
        }
        inAppPopupJob?.cancel()
        if (packageName in latestSettings.whitelist) {
            stopScanSession()
            return
        }
        if (fromLauncher) {
            startScanSession(packageName, now)
        } else {
            // App switch / notification launch / service reconnect: no splash
            // heuristic scan. Only exact custom rules may act later.
            stopScanSession()
        }
    }

    private fun startScanSession(packageName: String, startedAt: Long) {
        if (!appPreferences.isUserAgreementAccepted) {
            stopScanSession()
            return
        }
        scanJob?.cancel()
        val session = ScanSession(packageName = packageName, startedAt = startedAt)
        activeSession = session

        scanJob = serviceScope.launch {
            try {
                while (isActive) {
                    if (!appPreferences.isUserAgreementAccepted) break
                    val current = activeSession
                    if (current !== session) break
                    if (currentForegroundPackage != packageName) break
                    if (packageName == this@AdSkipAccessibilityService.packageName) break
                    if (isAutomationPaused()) break

                    val now = SystemClock.elapsedRealtime()
                    if (now - startedAt > SkipEngine.SPLASH_PHASE_MS) break

                    val roots = getAllWindowRoots()
                    if (roots.isNotEmpty() && !isAutomationPaused()) {
                        val result = skipEngine.scan(
                            packageName = packageName,
                            sessionStartedAt = startedAt,
                            now = now,
                            roots = roots,
                            isSplashPhase = true,
                            pageSessionKey = currentPageSessionKey(),
                        )
                        if (result != null) {
                            // A successful skip ends this polling session at once.
                            activeSession = null
                            recordSkip(packageName, result)
                            break
                        }
                    }
                    delay(SCAN_INTERVAL_MS)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                Log.e(TAG, "Splash scan failed for $packageName", error)
            } finally {
                if (activeSession === session) {
                    activeSession = null
                }
            }
        }
    }

    /**
     * Event-driven custom-rule watch. It only runs after the four-second splash
     * phase, is throttled to 300 ms, and the engine evaluates user rules only.
     */
    private fun requestInAppPopupScanIfEligible(
        packageName: String,
        eventClassName: String,
    ) {
        if (!appPreferences.isUserAgreementAccepted) return
        if (isAutomationPaused()) return
        if (packageName == this.packageName) return
        if (isSystemCriticalPackage(packageName)) return
        if (packageName != currentForegroundPackage) return
        if (packageName in latestSettings.whitelist) return
        if (activeSession != null) return

        val now = SystemClock.elapsedRealtime()
        if (now - foregroundStartedAt < SkipEngine.SPLASH_PHASE_MS) return
        if (now - lastInAppPopupScanAt < IN_APP_POPUP_THROTTLE_MS) return
        if (inAppPopupJob?.isActive == true) return

        lastInAppPopupScanAt = now
        inAppPopupJob = serviceScope.launch {
            try {
                // Let the dialog finish inflating before reading the new layer.
                delay(60)
                if (!appPreferences.isUserAgreementAccepted) return@launch
                if (currentForegroundPackage != packageName) return@launch
                val roots = getAllWindowRoots()
                if (roots.isEmpty()) return@launch
                val result = skipEngine.scanInAppPopup(
                    packageName = packageName,
                    activityClassName = currentActivityClassName,
                    pageSessionKey = currentPageSessionKey(),
                    roots = roots,
                    eventIsPopup = isPopupLikeClassName(eventClassName),
                    now = SystemClock.elapsedRealtime(),
                )
                if (result != null) {
                    recordSkip(packageName, result)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                Log.e(TAG, "In-app popup scan failed for $packageName", error)
            }
        }
    }

    private fun eventHasCloseSignal(event: AccessibilityEvent): Boolean {
        return eventTexts(event).any { text ->
            text.equals("x", ignoreCase = true) ||
                text == "×" ||
                text == "✕" ||
                POPUP_EVENT_CLOSE_WORDS.any { word ->
                    word.length > 1 && text.contains(word)
                }
        }
    }

    private fun eventTexts(event: AccessibilityEvent): List<String> = buildList {
        event.text.forEach { value ->
            val normalized = value.toString()
                .replace(INVISIBLE_OR_WHITESPACE_REGEX, "")
            if (normalized.isNotEmpty()) add(normalized)
        }
        val description = event.contentDescription?.toString().orEmpty()
            .replace(INVISIBLE_OR_WHITESPACE_REGEX, "")
        if (description.isNotEmpty()) add(description)
    }

    private fun isPopupLikeClassName(className: String): Boolean {
        if (className.isBlank()) return false
        val value = className.substringAfterLast('.').lowercase()
        return value.contains("dialog") ||
            value.contains("popup") ||
            value.contains("sheet") ||
            value.contains("modal") ||
            value.contains("overlay")
    }

    /**
     * Returns every interactive window root. Multi-window splash dialogs often
     * live outside rootInActiveWindow, so scanning only the active root misses
     * them. Falls back to rootInActiveWindow if the windows list is unavailable.
     */
    fun getAllWindowRoots(): List<AccessibilityNodeInfo> {
        if (!appPreferences.isUserAgreementAccepted) return emptyList()
        val roots = mutableListOf<AccessibilityNodeInfo>()
        runCatching {
            windows.forEach { window ->
                if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    return@forEach
                }
                val root = window.root ?: return@forEach
                val rootPackage = root.packageName?.toString().orEmpty()
                // Never pass our own overlay or any protected system surface
                // into the engine.
                if (rootPackage == packageName) return@forEach
                if (isSystemCriticalPackage(rootPackage)) {
                    return@forEach
                }
                roots.add(root)
            }
        }
        if (roots.isEmpty()) {
            val activeRoot = rootInActiveWindow ?: return emptyList()
            val activePackage = activeRoot.packageName?.toString().orEmpty()
            if (activePackage == packageName) return emptyList()
            if (isSystemCriticalPackage(activePackage)) {
                return emptyList()
            }
            roots.add(activeRoot)
        }
        return roots.distinctBy { root ->
            runCatching { root.windowId }.getOrDefault(-1)
        }
    }

    private fun isOwnWindowRoot(root: AccessibilityNodeInfo): Boolean =
        root.packageName?.toString() == packageName

    private fun currentPageSessionKey(): String =
        "${currentForegroundPackage.orEmpty()}|${currentActivityClassName}|$pageSessionStartedAt"

    private fun isSystemCriticalPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return true
        if (packageName in NON_APP_PACKAGES) return true
        if (packageName in launcherPackages) return true
        val lower = packageName.lowercase()
        return lower.contains("packageinstaller") ||
            lower.contains("permissioncontroller") ||
            lower.endsWith(".settings") ||
            lower.contains("inputmethod") ||
            lower.endsWith(".ime")
    }

    private fun stopScanSession() {
        activeSession = null
        scanJob?.cancel()
        scanJob = null
    }

    private fun recordSkip(packageName: String, result: SkipEngine.SkipResult) {
        serviceScope.launch {
            app.container.preferences.recordSkip(
                InterceptLog(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    type = InterceptLogType.SKIP_ADS,
                    packageName = packageName,
                    trigger = result.trigger,
                    detail = result.detail,
                ),
            )
        }
    }

    private fun handleInterceptEvent(event: JumpInterceptor.InterceptEvent) {
        if (!appPreferences.isUserAgreementAccepted) return
        serviceScope.launch {
            val log = InterceptLog(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                type = event.type,
                packageName = event.packageName,
                sourcePackage = event.sourcePackage,
                trigger = event.trigger,
                detail = event.detail,
            )
            when (event.type) {
                InterceptLogType.BLOCK_JUMP -> {
                    app.container.preferences.recordJumpBlock(log)
                    val settings = app.container.preferences.currentSettings()
                    val labels = withContext(Dispatchers.IO) {
                        val source = event.sourcePackage.orEmpty()
                        app.container.appRepository.labelForPackage(source) to
                            app.container.appRepository.labelForPackage(event.packageName)
                    }
                    NotificationHelper.showJumpBlocked(
                        context = this@AdSkipAccessibilityService,
                        todayCount = settings.blockCount,
                        sourceLabel = labels.first,
                        targetLabel = labels.second,
                    )
                }

                InterceptLogType.BLOCK_POPUP -> {
                    app.container.preferences.recordPopupBlock(log)
                }

                InterceptLogType.SKIP_ADS -> {
                    app.container.preferences.recordSkip(log)
                }
            }
        }
    }

    override fun onInterrupt() {
        stopScanSession()
        inAppPopupJob?.cancel()
    }

    override fun onDestroy() {
        runtimeReady = false
        if (runningInstance === this) {
            runningInstance = null
        }
        keepAliveJob?.cancel()
        inAppPopupJob?.cancel()
        keepAliveStarted = false
        KeepAliveHelper.stopGuard(this)
        app.container.layoutInspector.detachForServiceStop()
        serviceScope.cancel()
        super.onDestroy()
    }

    private data class ScanSession(
        val packageName: String,
        val startedAt: Long,
    )

    companion object {
        private const val TAG = "AdSkipService"
        private const val SCAN_INTERVAL_MS = 250L
        private const val IN_APP_POPUP_THROTTLE_MS = 300L
        private const val KEEP_ALIVE_REFRESH_MS = 15 * 60 * 1000L
        private const val MAX_CAPTURE_NODES = 4_000

        private val INVISIBLE_OR_WHITESPACE_REGEX = Regex("[\\s\u200B-\u200D\uFEFF]")
        private val POPUP_EVENT_CLOSE_WORDS = setOf(
            "关闭",
            "关闭弹窗",
            "关闭广告",
            "跳过",
            "放弃优惠",
            "残忍离开",
            "暂不需要",
            "我知道了",
        )

        @Volatile
        private var runningInstance: AdSkipAccessibilityService? = null

        val NON_APP_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.inputmethod.latin",
            "com.google.android.inputmethod.latin",
            "com.sohu.inputmethod.sogou",
            "com.iflytek.inputmethod",
            "com.baidu.input",
        )

        fun currentInstance(): AdSkipAccessibilityService? = runningInstance
    }
}
