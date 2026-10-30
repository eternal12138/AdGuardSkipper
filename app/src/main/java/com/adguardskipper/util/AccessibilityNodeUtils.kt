package com.adguardskipper.util

import android.accessibilityservice.AccessibilityService
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

object AccessibilityNodeUtils {
    /**
     * Breadth-first flattening with a hard cap. Accessibility trees can be very
     * large on web-heavy splash screens, so this keeps a single scan bounded.
     */
    fun flatten(root: AccessibilityNodeInfo, limit: Int = 700): List<AccessibilityNodeInfo> {
        val result = ArrayList<AccessibilityNodeInfo>(minOf(limit, 128))
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)

        while (queue.isNotEmpty() && result.size < limit) {
            val node = queue.removeFirst()
            val isVisible = runCatching { node.isVisibleToUser }.getOrDefault(false)
            if (node !== root && !isVisible) continue
            result.add(node)

            val childCount = runCatching { node.childCount }.getOrDefault(0)
            for (index in 0 until childCount) {
                if (result.size + queue.size >= limit) break
                runCatching { node.getChild(index) }
                    .getOrNull()
                    ?.let(queue::addLast)
            }
        }
        return result
    }

    fun textOf(node: AccessibilityNodeInfo): String {
        val label = runCatching { node.text?.toString()?.trim().orEmpty() }.getOrDefault("")
        val description = runCatching {
            node.contentDescription?.toString()?.trim().orEmpty()
        }.getOrDefault("")
        return when {
            label.isNotEmpty() && description.isNotEmpty() && label != description ->
                "$label $description"
            label.isNotEmpty() -> label
            else -> description
        }
    }

    fun normalizedViewId(node: AccessibilityNodeInfo): String {
        val raw = runCatching { node.viewIdResourceName.orEmpty() }.getOrDefault("")
        return raw.substringAfterLast(':').substringAfterLast('/').lowercase()
    }

    fun boundsOf(node: AccessibilityNodeInfo): Rect? {
        val bounds = Rect()
        val success = runCatching { node.getBoundsInScreen(bounds) }.isSuccess
        if (success && bounds.width() > 0 && bounds.height() > 0) {
            return bounds
        }
        return null
    }

    fun centerOf(node: AccessibilityNodeInfo): PointF? {
        val bounds = boundsOf(node) ?: return null
        return PointF(bounds.exactCenterX(), bounds.exactCenterY())
    }

    fun findClickableAncestor(
        node: AccessibilityNodeInfo,
        maxDepth: Int = 4,
    ): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth <= maxDepth) {
            if (runCatching { current?.isClickable == true }.getOrDefault(false)) {
                return current
            }
            current = runCatching { current?.parent }.getOrNull()
            depth++
        }
        return null
    }

    fun containsAny(text: String, needles: Collection<String>): Boolean =
        needles.any { needle -> text.contains(needle, ignoreCase = true) }

    fun rootsOf(service: AccessibilityService): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        runCatching {
            service.windows
                .filter { it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }
                .mapNotNull { window -> window.root }
                .forEach(roots::add)
        }
        if (roots.isEmpty()) {
            service.rootInActiveWindow?.let(roots::add)
        }
        return roots.distinctBy { root ->
            val rect = boundsOf(root)
            "${root.packageName}:${rect?.toShortString()}"
        }
    }
}

object UiActions {
    /**
     * Tries ACTION_CLICK on the node (or its nearest clickable ancestor), then
     * falls back to a real 40 ms touch gesture at the node center.
     */
    suspend fun clickOrGesture(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        val clickTarget = if (runCatching { node.isClickable }.getOrDefault(false)) {
            node
        } else {
            AccessibilityNodeUtils.findClickableAncestor(node)
        }
        if (
            clickTarget != null &&
            runCatching { clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                .getOrDefault(false)
        ) {
            return true
        }
        val center = AccessibilityNodeUtils.centerOf(node) ?: return false
        return tap(service, center.x, center.y)
    }

    /** Dispatches a real finger tap. The stroke is held down for 40 ms. */
    suspend fun tap(service: AccessibilityService, x: Float, y: Float): Boolean {
        val metrics = service.resources.displayMetrics
        val safeX = x.coerceIn(1f, (metrics.widthPixels - 2).coerceAtLeast(1).toFloat())
        val safeY = y.coerceIn(1f, (metrics.heightPixels - 2).coerceAtLeast(1).toFloat())
        val path = Path().apply { moveTo(safeX, safeY) }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, 40L))
            .build()

        return withTimeoutOrNull(1_200L) {
            suspendCancellableCoroutine { continuation ->
                val callback = object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                }
                val accepted = runCatching {
                    service.dispatchGesture(gesture, callback, null)
                }.getOrDefault(false)
                if (!accepted && continuation.isActive) {
                    continuation.resume(false)
                }
            }
        } ?: false
    }
}
