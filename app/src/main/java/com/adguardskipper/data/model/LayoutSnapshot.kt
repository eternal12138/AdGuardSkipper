package com.adguardskipper.data.model

/**
 * An immutable snapshot produced by the accessibility service for the layout
 * inspector. Only visible nodes are captured.
 */
data class LayoutSnapshot(
    val packageName: String,
    val activityName: String,
    val capturedAt: Long,
    val screenWidth: Int,
    val screenHeight: Int,
    val truncated: Boolean,
    val nodes: List<CapturedNode>,
)

data class CapturedNode(
    val depth: Int,
    val className: String,
    val text: String,
    val contentDescription: String,
    val viewIdResourceName: String,
    val clickable: Boolean,
    val visible: Boolean,
    val boundsInScreen: NodeBounds,
)

data class NodeBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    fun toShortString(): String = "[$left,$top][$right,$bottom]"
}
