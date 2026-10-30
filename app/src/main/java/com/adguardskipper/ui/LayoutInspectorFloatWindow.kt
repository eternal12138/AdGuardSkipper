package com.adguardskipper.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.adguardskipper.R
import com.adguardskipper.data.AppPreferences
import com.adguardskipper.data.model.CapturedNode
import com.adguardskipper.data.model.LayoutSnapshot
import com.adguardskipper.data.model.NodeBounds
import com.adguardskipper.service.AdSkipAccessibilityService
import com.adguardskipper.util.PermissionUtils
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Side-edge floating ball and node inspector overlay.
 *
 * The ball is intentionally lightweight. A tap asks the live
 * AccessibilityService for a recursive visible-node snapshot, writes the full
 * tree to app-private storage, then opens a scrollable overlay where a node tap
 * copies its raw ViewId and persists the normalized ViewId as a local rule.
 */
@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class LayoutInspectorFloatWindow(
    context: Context,
    private val preferences: AppPreferences,
) {
    private val appContext = context.applicationContext
    private val windowManager =
        appContext.getSystemService(WindowManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val localPrefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var ballView: ImageView? = null
    private var panelView: View? = null
    private var captureInProgress = false
    private var aimSaveInFlight = false

    var isInspectorDialogShowing: Boolean = false
        private set

    val isShowing: Boolean
        get() = ballView != null || panelView != null

    val isEnabled: Boolean
        get() = localPrefs.getBoolean(KEY_ENABLED, false)

    /**
     * Shows the ball if overlay permission is available. Returns false if the
     * permission was revoked or WindowManager rejected the window.
     */
    fun show(): Boolean {
        if (!PermissionUtils.canDrawOverlays(appContext)) {
            toast("请先授予悬浮窗权限")
            return false
        }
        if (panelView != null || ballView != null) return true
        val added = runCatching {
            addBall()
            localPrefs.edit().putBoolean(KEY_ENABLED, true).apply()
            true
        }.onFailure { error ->
            toast("悬浮球启动失败：${error.javaClass.simpleName}")
        }.getOrDefault(false)
        return added
    }

    fun hide() {
        removePanel()
        removeBall()
        localPrefs.edit().putBoolean(KEY_ENABLED, false).apply()
    }

    /**
     * Removes overlay windows when the accessibility service disconnects but
     * keeps the enabled preference so the ball can return on reconnect.
     */
    fun detachForServiceStop() {
        removePanel()
        removeBall()
    }

    /**
     * Called when the accessibility service reconnects so a previously enabled
     * ball survives process restarts caused by OEM background cleaners.
     */
    fun restoreIfEnabled() {
        if (!localPrefs.getBoolean(KEY_ENABLED, false)) return
        if (!PermissionUtils.canDrawOverlays(appContext)) {
            localPrefs.edit().putBoolean(KEY_ENABLED, false).apply()
            return
        }
        if (isShowing) return
        runCatching {
            addBall()
        }.onFailure {
            localPrefs.edit().putBoolean(KEY_ENABLED, false).apply()
        }
    }

    /** Hides an orphaned overlay if the user revoked overlay permission. */
    fun syncWithPermission() {
        if (!PermissionUtils.canDrawOverlays(appContext) && isShowing) {
            removePanel()
            removeBall()
            localPrefs.edit().putBoolean(KEY_ENABLED, false).apply()
        }
    }

    private fun addBall() {
        val manager = windowManager ?: error("WindowManager unavailable")
        if (ballView != null) return
        val metrics = appContext.resources.displayMetrics
        val size = dp(56)
        val maxX = (metrics.widthPixels - size).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - size - dp(72)).coerceAtLeast(dp(24))

        val image = ImageView(appContext).apply {
            setImageResource(R.drawable.ic_inspect_ball)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ACCENT)
                setStroke(dp(2), Color.argb(90, 255, 255, 255))
            }
            elevation = dp(10).toFloat()
            contentDescription = "布局抓取悬浮球"
            isClickable = true
            setOnClickListener { openInspector() }
        }

        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val rightSide = localPrefs.getString(KEY_SIDE, SIDE_RIGHT) != SIDE_LEFT
            x = if (rightSide) maxX - dp(8) else dp(8)
            y = localPrefs.getInt(KEY_Y, metrics.heightPixels / 3)
                .coerceIn(dp(24), maxY)
        }

        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragged = false
        val touchSlop = ViewConfiguration.get(appContext).scaledTouchSlop

        image.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragged = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragged && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragged = true
                    }
                    if (dragged) {
                        params.x = (startX + dx.toInt()).coerceIn(0, maxX)
                        params.y = (startY + dy.toInt()).coerceIn(dp(24), maxY)
                        runCatching { manager.updateViewLayout(view, params) }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!dragged) {
                        view.performClick()
                    } else {
                        snapToEdge(params, size, maxX, maxY)
                        runCatching { manager.updateViewLayout(image, params) }
                        saveBallPosition(params, metrics.widthPixels, size)
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }

        manager.addView(image, params)
        ballView = image
    }

    private fun snapToEdge(
        params: WindowManager.LayoutParams,
        size: Int,
        maxX: Int,
        maxY: Int,
    ) {
        val centerX = params.x + size / 2
        val width = appContext.resources.displayMetrics.widthPixels
        params.x = if (centerX < width / 2) {
            dp(8)
        } else {
            (maxX - dp(8)).coerceAtLeast(0)
        }
        params.y = params.y.coerceIn(dp(24), maxY)
    }

    private fun saveBallPosition(
        params: WindowManager.LayoutParams,
        width: Int,
        size: Int,
    ) {
        val side = if (params.x + size / 2 < width / 2) SIDE_LEFT else SIDE_RIGHT
        localPrefs.edit()
            .putString(KEY_SIDE, side)
            .putInt(KEY_Y, params.y)
            .apply()
    }

    private fun removeBall() {
        val image = ballView ?: return
        runCatching { windowManager?.removeViewImmediate(image) }
        ballView = null
    }

    private fun openInspector() {
        if (captureInProgress) return
        val service = AdSkipAccessibilityService.currentInstance()
        if (service == null) {
            toast("请先开启无障碍服务，再抓取当前界面")
            return
        }
        val ball = ballView ?: return
        captureInProgress = true
        ball.isClickable = false
        ball.visibility = View.INVISIBLE

        scope.launch {
            try {
                // Let WindowManager/accessibility recompute the visible tree
                // without our own floating ball before dumping.
                delay(80)
                if (ballView !== ball) return@launch
                val activeService = AdSkipAccessibilityService.currentInstance()
                if (activeService == null) {
                    restoreBallInteraction(ball)
                    toast("无障碍服务已断开，请重新开启后再抓取")
                    return@launch
                }

                val snapshot = runCatching {
                    dumpCurrentWindowNodes(activeService)
                }.onFailure { error ->
                    toast("抓取失败：${error.message ?: error.javaClass.simpleName}")
                }.getOrNull()

                if (snapshot == null) {
                    restoreBallInteraction(ball)
                    toast("当前没有可读取的目标应用窗口节点")
                    return@launch
                }

                // Restore the ball first, then swap it for the review panel.
                restoreBallInteraction(ball)
                removeBall()
                showAimOverlay(snapshot)
            } finally {
                captureInProgress = false
            }
        }
    }

    private fun dumpCurrentWindowNodes(
        service: AdSkipAccessibilityService,
    ): LayoutSnapshot? {
        // The service performs window/node-level self-package filtering and
        // returns only nodes from the foreground target application.
        return service.dumpCurrentWindowNodes()
    }

    private fun restoreBallInteraction(ball: ImageView) {
        if (ballView !== ball) return
        ball.visibility = View.VISIBLE
        ball.isClickable = true
    }

    private fun updateInspectorDialogVisibility(showing: Boolean) {
        if (isInspectorDialogShowing == showing) return
        isInspectorDialogShowing = showing
        AdSkipAccessibilityService.currentInstance()
            ?.onInspectorDialogVisibilityChanged(showing)
    }

    private fun showAimOverlay(snapshot: LayoutSnapshot) {
        val manager = windowManager ?: run {
            toast("WindowManager 不可用")
            restoreBallAfterPanel()
            return
        }
        removePanel()
        aimSaveInFlight = false

        val candidates = buildAimCandidates(snapshot)
        val recommended = recommendAimCandidates(candidates)
        val overlay = FrameLayout(appContext)
        val aimView = AimOverlayView(snapshot, candidates, recommended)
        overlay.addView(
            aimView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        val prompt = text(
            "请直接点击屏幕上的【跳过/关闭】广告按钮位置（或点击右上角退出）",
            14f,
            Color.WHITE,
        ).apply {
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(0xE616265C.toInt(), dp(18).toFloat())
            maxLines = 2
            isClickable = true
        }
        overlay.addView(
            prompt,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(34)
                leftMargin = dp(16)
                rightMargin = dp(78)
            },
        )

        val exit = text("退出", 13f, Color.WHITE, bold = true).apply {
            setPadding(dp(13), dp(8), dp(13), dp(8))
            background = rounded(0xE616265C.toInt(), dp(16).toFloat())
            setOnClickListener { closePanel() }
        }
        overlay.addView(
            exit,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dp(34)
                rightMargin = dp(14)
            },
        )

        val toggle = text("只看推荐按钮", 12f, PANEL_TITLE, bold = true).apply {
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = rounded(0xE6FFFFFF.toInt(), dp(16).toFloat())
        }
        overlay.addView(
            toggle,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                topMargin = dp(100)
                leftMargin = dp(16)
            },
        )

        val recommendedList = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(0xF2FFFFFF.toInt(), dp(18).toFloat())
            elevation = dp(10).toFloat()
            visibility = View.GONE
            isClickable = true
        }
        overlay.addView(
            recommendedList,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.TOP
                topMargin = dp(142)
                leftMargin = dp(16)
                rightMargin = dp(16)
            },
        )

        val card = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
            background = rounded(0xFFFFFFFF.toInt(), dp(22).toFloat())
            elevation = dp(18).toFloat()
            visibility = View.GONE
            isClickable = true
        }
        val cardTitle = text("已选中目标区域", 18f, PANEL_TITLE, bold = true)
        card.addView(cardTitle)
        val cardSubtitle = text("", 12f, MUTED_TEXT).apply {
            setPadding(0, dp(3), 0, dp(11))
        }
        card.addView(cardSubtitle)

        val smartButton = text("⚡ 智能屏蔽此按钮（推荐）", 15f, Color.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(15), dp(14), dp(15))
            background = rounded(0xFF2F6BFF.toInt(), dp(15).toFloat())
        }
        card.addView(
            smartButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        val forceButton = text("🎯 强制锁定此位置点击", 15f, 0xFF16265C.toInt(), bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(15), dp(14), dp(15))
            background = rounded(0xFFE7F3FF.toInt(), dp(15).toFloat())
        }
        card.addView(
            forceButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )
        overlay.addView(
            card,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.BOTTOM
                leftMargin = dp(14)
                rightMargin = dp(14)
                bottomMargin = dp(24)
            },
        )

        var currentSelection: AimSelection? = null

        fun present(selection: AimSelection) {
            currentSelection = selection
            val node = selection.node
            val position = node?.let { positionLabel(it.boundsInScreen, snapshot) } ?: "屏幕位置"
            val label = node?.let(::aimNodeLabel).orEmpty()
            cardTitle.text = when {
                node == null -> "已选中目标区域"
                label.isNotBlank() -> "已选中：$label"
                else -> "已选中：图标按钮 [位于$position]"
            }
            cardSubtitle.text = if (node == null) {
                "将直接点击您按下的屏幕位置"
            } else {
                "位置：$position · 下次自动跳过"
            }
            card.visibility = View.VISIBLE
            card.alpha = 0f
            card.animate().alpha(1f).setDuration(120).start()
        }

        aimView.onTargetSelected = { selection -> present(selection) }
        smartButton.setOnClickListener {
            currentSelection?.let { saveSmartAimRule(snapshot, it) }
        }
        forceButton.setOnClickListener {
            currentSelection?.let { saveAimCoordinate(snapshot, it) }
        }

        recommended.forEachIndexed { index, candidate ->
            val node = candidate.node
            val label = aimNodeLabel(node).ifBlank { "图标按钮" }
            val position = positionLabel(node.boundsInScreen, snapshot)
            val row = text(
                "${index + 1}. [$position] $label  |  点击一键拦截",
                13f,
                PANEL_TITLE,
            ).apply {
                setPadding(dp(11), dp(10), dp(11), dp(10))
                maxLines = 2
                background = ripple(rounded(0x00FFFFFF, dp(12).toFloat()))
                setOnClickListener {
                    val centerX = (node.boundsInScreen.left + node.boundsInScreen.right) / 2f
                    val centerY = (node.boundsInScreen.top + node.boundsInScreen.bottom) / 2f
                    saveSmartAimRule(
                        snapshot = snapshot,
                        selection = AimSelection(node, centerX, centerY),
                    )
                }
            }
            recommendedList.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        var onlyRecommended = false
        toggle.setOnClickListener {
            onlyRecommended = !onlyRecommended
            aimView.onlyRecommended = onlyRecommended
            recommendedList.visibility =
                if (onlyRecommended && recommended.isNotEmpty()) View.VISIBLE else View.GONE
            toggle.background = rounded(
                if (onlyRecommended) 0xFF2F6BFF.toInt() else 0xE6FFFFFF.toInt(),
                dp(16).toFloat(),
            )
            toggle.setTextColor(if (onlyRecommended) Color.WHITE else PANEL_TITLE)
            if (onlyRecommended && recommended.isEmpty()) {
                toast("暂未发现明显按钮，可直接点击屏幕位置")
            }
        }

        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        val added = runCatching {
            manager.addView(overlay, params)
            panelView = overlay
            true
        }.onFailure {
            toast("瞄准层打开失败：${it.javaClass.simpleName}")
        }.getOrDefault(false)

        if (!added) {
            updateInspectorDialogVisibility(false)
            restoreBallAfterPanel()
            return
        }
        updateInspectorDialogVisibility(true)

        // Keep the JSON snapshot for diagnostics, but never make users read it.
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { writeSnapshot(snapshot) } }
        }
    }

    private fun closePanel() {
        removePanel()
        restoreBallAfterPanel()
    }

    private fun removePanel() {
        val panel = panelView
        if (panel != null) {
            runCatching { windowManager?.removeViewImmediate(panel) }
            panelView = null
        }
        updateInspectorDialogVisibility(false)
    }

    private fun restoreBallAfterPanel() {
        if (localPrefs.getBoolean(KEY_ENABLED, true) &&
            PermissionUtils.canDrawOverlays(appContext) &&
            ballView == null
        ) {
            runCatching { addBall() }
        }
    }

    private fun buildAimCandidates(snapshot: LayoutSnapshot): List<AimCandidate> {
        val maxWidth = (snapshot.screenWidth * 0.4f).toInt()
        val maxHeight = (snapshot.screenHeight * 0.4f).toInt()
        val minSize = dp(8)
        val seen = HashSet<String>()
        val result = mutableListOf<AimCandidate>()

        snapshot.nodes.forEach { node ->
            if (!node.visible) return@forEach
            val bounds = node.boundsInScreen
            val width = bounds.right - bounds.left
            val height = bounds.bottom - bounds.top
            if (width < minSize || height < minSize) return@forEach
            if (width > maxWidth || height > maxHeight) return@forEach

            val label = aimNodeLabel(node)
            val viewId = node.viewIdResourceName.trim()
            val simpleClass = node.className.substringAfterLast('.').lowercase()
            val container = simpleClass in AIM_CONTAINER_CLASSES
            val imageOrButton = simpleClass.contains("imageview") ||
                simpleClass.contains("imagebutton") ||
                simpleClass.contains("button")
            val hasCloseHint = hasAimCloseText(node) || hasAimCloseId(node)
            if (!node.clickable && !hasCloseHint && !imageOrButton) return@forEach
            if (container && label.isBlank() && viewId.isBlank()) return@forEach

            val key = "${bounds.left}:${bounds.top}:${bounds.right}:${bounds.bottom}:" +
                "${viewId.lowercase()}:${label.lowercase()}"
            if (!seen.add(key)) return@forEach

            result += AimCandidate(
                node = node,
                suspected = isSuspectedAimButton(node, snapshot),
                score = aimRecommendationScore(node, snapshot),
                area = width.toLong() * height.toLong(),
            )
        }
        return result.sortedBy { it.area }.take(MAX_AIM_CANDIDATES)
    }

    private fun recommendAimCandidates(candidates: List<AimCandidate>): List<AimCandidate> {
        val eligible = candidates.filter { candidate ->
            aimNodeLabel(candidate.node).isNotBlank() ||
                candidate.node.viewIdResourceName.isNotBlank()
        }.sortedWith(
            compareByDescending<AimCandidate> { it.score }.thenBy { it.area },
        )
        val preferred = eligible.filter { it.score >= 3 }
        return if (preferred.size >= 3) {
            preferred.take(MAX_AIM_RECOMMENDATIONS)
        } else {
            eligible.take(4)
        }
    }

    private fun aimRecommendationScore(
        node: CapturedNode,
        snapshot: LayoutSnapshot,
    ): Int {
        var score = 0
        if (hasAimCloseText(node)) score += 8
        if (hasAimCloseId(node)) score += 6
        if (isAimTopRight(node.boundsInScreen, snapshot)) score += 4
        if (isAimBottomCenter(node.boundsInScreen, snapshot)) score += 4
        if (aimNodeLabel(node).isNotBlank()) score += 2
        if (node.viewIdResourceName.isNotBlank()) score += 2
        if (node.clickable) score += 1
        return score
    }

    private fun isSuspectedAimButton(
        node: CapturedNode,
        snapshot: LayoutSnapshot,
    ): Boolean =
        hasAimCloseText(node) ||
            hasAimCloseId(node) ||
            isAimTopRight(node.boundsInScreen, snapshot) ||
            isAimBottomCenter(node.boundsInScreen, snapshot)

    private fun hasAimCloseText(node: CapturedNode): Boolean {
        val label = compactAimText(aimNodeLabel(node)).lowercase()
        if (label.isEmpty()) return false
        if (label == "x" || label == "×" || label == "✕") return true
        return label.contains("跳过") ||
            label.contains("关闭") ||
            label.contains("skip") ||
            label.contains("close") ||
            label.contains("dismiss")
    }

    private fun hasAimCloseId(node: CapturedNode): Boolean {
        val id = normalizeViewId(node.viewIdResourceName)
        return id.contains("skip") ||
            id.contains("close") ||
            id.contains("dismiss")
    }

    private fun isAimTopRight(bounds: NodeBounds, snapshot: LayoutSnapshot): Boolean =
        bounds.left > snapshot.screenWidth * 0.65f &&
            bounds.top < snapshot.screenHeight * 0.28f

    private fun isAimBottomCenter(bounds: NodeBounds, snapshot: LayoutSnapshot): Boolean {
        val centerX = (bounds.left + bounds.right) / 2f
        val centerY = (bounds.top + bounds.bottom) / 2f
        return centerY in snapshot.screenHeight * 0.66f..snapshot.screenHeight * 0.92f &&
            abs(centerX - snapshot.screenWidth / 2f) <= snapshot.screenWidth * 0.12f
    }

    private fun positionLabel(bounds: NodeBounds, snapshot: LayoutSnapshot): String {
        val centerY = (bounds.top + bounds.bottom) / 2f
        return when {
            isAimTopRight(bounds, snapshot) -> "右上角"
            isAimBottomCenter(bounds, snapshot) -> "底部居中"
            bounds.top < snapshot.screenHeight * 0.18f -> "顶部"
            centerY < snapshot.screenHeight * 0.30f -> "上方"
            centerY > snapshot.screenHeight * 0.70f -> "下方"
            bounds.left < snapshot.screenWidth * 0.32f -> "左侧"
            bounds.right > snapshot.screenWidth * 0.68f -> "右侧"
            else -> "屏幕中部"
        }
    }

    private fun aimNodeLabel(node: CapturedNode): String =
        node.text.trim()
            .ifBlank { node.contentDescription.trim() }
            .replace(Regex("[\\s\\u200B-\\u200D\\uFEFF]+"), " ")
            .trim()
            .take(28)

    private fun compactAimText(raw: String): String =
        raw.replace(Regex("[\\s\\u200B-\\u200D\\uFEFF]"), "")

    private fun usableAimText(node: CapturedNode): String {
        val compact = compactAimText(aimNodeLabel(node))
        if (compact.isEmpty()) return ""
        if (compact.matches(Regex("^[0-9]+[sS秒]?$"))) return ""
        val withoutCountdown = compact.replace(Regex("[0-9]+[sS秒]?$"), "")
        return withoutCountdown.ifBlank { compact }.take(32)
    }

    private fun saveSmartAimRule(snapshot: LayoutSnapshot, selection: AimSelection) {
        val packageName = snapshot.packageName
        if (packageName.isBlank()) {
            toast("当前应用包名未知，无法添加规则")
            return
        }
        if (aimSaveInFlight) return
        aimSaveInFlight = true

        val node = selection.node
        val viewId = node?.viewIdResourceName?.trim().orEmpty()
        val textRule = node?.let(::usableAimText).orEmpty()
        scope.launch {
            runCatching {
                when {
                    viewId.isNotBlank() -> {
                        preferences.addCustomViewId(
                            packageName = packageName,
                            rawViewId = normalizeViewId(viewId),
                            activityName = snapshot.activityName,
                            globalInAppRule = true,
                        )
                    }

                    textRule.isNotBlank() -> {
                        preferences.addCustomText(
                            packageName = packageName,
                            rawText = textRule,
                            activityName = snapshot.activityName,
                            globalInAppRule = true,
                        )
                    }

                    else -> addAimCoordinateRule(snapshot, selection)
                }
            }
            aimSaveInFlight = false
            toast("已添加拦截规则，下次自动跳过")
            closePanel()
        }
    }

    private fun saveAimCoordinate(snapshot: LayoutSnapshot, selection: AimSelection) {
        if (snapshot.packageName.isBlank()) {
            toast("当前应用包名未知，无法添加规则")
            return
        }
        if (aimSaveInFlight) return
        aimSaveInFlight = true
        scope.launch {
            runCatching { addAimCoordinateRule(snapshot, selection) }
            aimSaveInFlight = false
            toast("已添加拦截规则，下次自动跳过")
            closePanel()
        }
    }

    private suspend fun addAimCoordinateRule(
        snapshot: LayoutSnapshot,
        selection: AimSelection,
    ): Boolean {
        val width = snapshot.screenWidth.coerceAtLeast(1)
        val height = snapshot.screenHeight.coerceAtLeast(1)
        return preferences.addCustomCoordinate(
            packageName = snapshot.packageName,
            percentX = (selection.tapX / width).coerceIn(0f, 1f),
            percentY = (selection.tapY / height).coerceIn(0f, 1f),
            activityName = snapshot.activityName,
            globalInAppRule = true,
        )
    }

    private fun normalizeViewId(raw: String): String =
        raw.trim().substringAfterLast(':').substringAfterLast('/').lowercase()

    private inner class AimOverlayView(
        private val snapshot: LayoutSnapshot,
        private val candidates: List<AimCandidate>,
        private val recommended: List<AimCandidate>,
    ) : View(appContext) {
        var onlyRecommended: Boolean = false
            set(value) {
                if (field == value) return
                field = value
                invalidate()
            }

        var onTargetSelected: ((AimSelection) -> Unit)? = null

        private var selectedNode: CapturedNode? = null
        private var downX = 0f
        private var downY = 0f
        private val location = IntArray(2)
        private val reusableRect = RectF()
        private val badgeRect = RectF()

        private val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFF35C7F4.toInt()
            strokeWidth = dp(2).toFloat()
            pathEffect = DashPathEffect(
                floatArrayOf(dp(7).toFloat(), dp(4).toFloat()),
                0f,
            )
        }
        private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFFE94B62.toInt()
            strokeWidth = dp(3).toFloat()
        }
        private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = dp(4).toFloat()
        }
        private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFFE94B62.toInt()
        }
        private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dp(10).toFloat()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        init {
            isClickable = true
            isFocusable = false
        }

        fun selectNode(node: CapturedNode, tapX: Float, tapY: Float) {
            selectedNode = node
            invalidate()
            onTargetSelected?.invoke(AimSelection(node, tapX, tapY))
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.argb(84, 12, 26, 64))

            getLocationOnScreen(location)
            val offsetX = location[0].toFloat()
            val offsetY = location[1].toFloat()
            val displayed = if (onlyRecommended) recommended else candidates

            displayed.forEach { candidate ->
                val bounds = candidate.node.boundsInScreen
                reusableRect.set(
                    bounds.left - offsetX,
                    bounds.top - offsetY,
                    bounds.right - offsetX,
                    bounds.bottom - offsetY,
                )
                if (reusableRect.width() <= 0f || reusableRect.height() <= 0f) {
                    return@forEach
                }
                if (candidate.suspected) {
                    canvas.drawRoundRect(
                        reusableRect,
                        dp(5).toFloat(),
                        dp(5).toFloat(),
                        redPaint,
                    )
                    drawSuspectedBadge(canvas, reusableRect)
                } else {
                    canvas.drawRoundRect(
                        reusableRect,
                        dp(5).toFloat(),
                        dp(5).toFloat(),
                        greenPaint,
                    )
                }
            }

            selectedNode?.let { node ->
                val bounds = node.boundsInScreen
                reusableRect.set(
                    bounds.left - offsetX,
                    bounds.top - offsetY,
                    bounds.right - offsetX,
                    bounds.bottom - offsetY,
                )
                canvas.drawRoundRect(
                    reusableRect,
                    dp(7).toFloat(),
                    dp(7).toFloat(),
                    selectedPaint,
                )
            }
        }

        private fun drawSuspectedBadge(canvas: Canvas, rect: RectF) {
            val text = "疑似跳过按钮"
            val textWidth = badgeTextPaint.measureText(text)
            val paddingX = dp(6).toFloat()
            val badgeHeight = dp(19).toFloat()
            val badgeWidth = textWidth + paddingX * 2
            val left = rect.left.coerceIn(0f, width - badgeWidth)
            val top = if (rect.top >= badgeHeight + dp(2).toFloat()) {
                rect.top - badgeHeight
            } else {
                rect.top
            }
            badgeRect.set(left, top, left + badgeWidth, top + badgeHeight)
            canvas.drawRoundRect(
                badgeRect,
                dp(5).toFloat(),
                dp(5).toFloat(),
                badgePaint,
            )
            canvas.drawText(
                text,
                badgeRect.left + paddingX,
                badgeRect.bottom - dp(5).toFloat(),
                badgeTextPaint,
            )
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    if (abs(event.x - downX) <= dp(12).toFloat() &&
                        abs(event.y - downY) <= dp(12).toFloat()
                    ) {
                        getLocationOnScreen(location)
                        handleTap(event.x + location[0], event.y + location[1])
                    }
                    return true
                }

                MotionEvent.ACTION_CANCEL -> return true
            }
            return true
        }

        private fun handleTap(screenX: Float, screenY: Float) {
            val displayed = if (onlyRecommended) recommended else candidates
            val hit = displayed
                .filter { candidate ->
                    val bounds = candidate.node.boundsInScreen
                    screenX >= bounds.left &&
                        screenX <= bounds.right &&
                        screenY >= bounds.top &&
                        screenY <= bounds.bottom
                }
                .minByOrNull { it.area }

            if (hit == null) {
                selectedNode = null
                invalidate()
                onTargetSelected?.invoke(AimSelection(null, screenX, screenY))
            } else {
                selectNode(hit.node, screenX, screenY)
            }
        }
    }

    private fun writeSnapshot(snapshot: LayoutSnapshot): File {
        val directory = File(appContext.filesDir, CAPTURE_DIR).apply { mkdirs() }
        val stamp = fileStampFormatter.format(Instant.ofEpochMilli(snapshot.capturedAt))
        val safePackage = snapshot.packageName
            .ifBlank { "unknown" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(directory, "layout_${safePackage}_$stamp.json")

        val nodes = JSONArray()
        snapshot.nodes.forEach { node ->
            nodes.put(
                JSONObject().apply {
                    put("depth", node.depth)
                    put("className", node.className)
                    put("text", node.text)
                    put("contentDescription", node.contentDescription)
                    put("viewIdResourceName", node.viewIdResourceName)
                    put("clickable", node.clickable)
                    put("visible", node.visible)
                    put(
                        "boundsInScreen",
                        JSONObject().apply {
                            put("left", node.boundsInScreen.left)
                            put("top", node.boundsInScreen.top)
                            put("right", node.boundsInScreen.right)
                            put("bottom", node.boundsInScreen.bottom)
                        },
                    )
                },
            )
        }

        val root = JSONObject().apply {
            put("schemaVersion", 2)
            put("packageName", snapshot.packageName)
            put("activityName", snapshot.activityName)
            put("capturedAt", snapshot.capturedAt)
            put("capturedAtIso", Instant.ofEpochMilli(snapshot.capturedAt).toString())
            put(
                "screen",
                JSONObject().apply {
                    put("width", snapshot.screenWidth)
                    put("height", snapshot.screenHeight)
                },
            )
            put("nodeCount", snapshot.nodes.size)
            put("truncated", snapshot.truncated)
            put("nodes", nodes)
        }
        file.writeText(root.toString(2), Charsets.UTF_8)
        return file
    }

    private fun toast(message: String) {
        Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun text(
        text: String,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false,
    ): TextView = TextView(appContext).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (bold) {
            setTypeface(typeface, Typeface.BOLD)
        }
        includeFontPadding = false
    }

    private fun rounded(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }

    private fun ripple(content: GradientDrawable): RippleDrawable =
        RippleDrawable(
            ColorStateList.valueOf(Color.argb(38, 47, 107, 255)),
            content,
            null,
        )

    private fun dp(value: Int): Int =
        (value * appContext.resources.displayMetrics.density).toInt()

    private data class AimCandidate(
        val node: CapturedNode,
        val suspected: Boolean,
        val score: Int,
        val area: Long,
    )

    private data class AimSelection(
        val node: CapturedNode?,
        val tapX: Float,
        val tapY: Float,
    )

    private companion object {
        const val PREFS_NAME = "layout_inspector"
        const val KEY_ENABLED = "enabled"
        const val KEY_SIDE = "side"
        const val KEY_Y = "y"
        const val SIDE_LEFT = "left"
        const val SIDE_RIGHT = "right"
        const val CAPTURE_DIR = "layout_captures"

        val PANEL_TITLE = Color.parseColor("#16265C")
        val MUTED_TEXT = Color.parseColor("#58769A")
        val ACCENT = Color.parseColor("#2F6BFF")

        val AIM_CONTAINER_CLASSES = setOf(
            "framelayout",
            "linearlayout",
            "relativelayout",
            "constraintlayout",
            "coordinatorlayout",
            "viewgroup",
        )

        const val MAX_AIM_CANDIDATES = 180
        const val MAX_AIM_RECOMMENDATIONS = 5

        val fileStampFormatter: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyyMMdd_HHmmss_SSS")
            .withZone(ZoneId.systemDefault())
    }
}
