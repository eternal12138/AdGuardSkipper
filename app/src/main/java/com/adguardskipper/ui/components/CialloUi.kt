package com.adguardskipper.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.adguardskipper.R
import com.adguardskipper.ui.theme.CialloPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A soft poster backdrop with the same sky gradient, bubbles and arcade stars
 * used by the "跳广" key art. Everything here is decoration only.
 */
@Composable
fun CialloBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to CialloPalette.SkyTop,
                    0.35f to CialloPalette.Sky,
                    0.72f to CialloPalette.Cloud,
                    1f to CialloPalette.Blush,
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val unit = width / 390f

            drawCircle(
                color = Color.White.copy(alpha = 0.34f),
                radius = width * 0.55f,
                center = Offset(width * 0.98f, height * 0.04f),
            )
            drawCircle(
                color = CialloPalette.Cyan.copy(alpha = 0.08f),
                radius = width * 0.40f,
                center = Offset(width * 0.02f, height * 0.44f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.38f),
                radius = width * 0.30f,
                center = Offset(width * 0.18f, height * 0.98f),
            )

            val stars = listOf(
                FloatingStar(0.07f, 0.055f, 6.5f, CialloPalette.Lemon, 0.55f),
                FloatingStar(0.91f, 0.10f, 8.5f, CialloPalette.Blue, 0.18f),
                FloatingStar(0.84f, 0.24f, 4.5f, CialloPalette.Lemon, 0.55f),
                FloatingStar(0.12f, 0.34f, 4.0f, CialloPalette.Coral, 0.42f),
                FloatingStar(0.95f, 0.46f, 6.0f, CialloPalette.Cyan, 0.30f),
                FloatingStar(0.05f, 0.63f, 7.5f, CialloPalette.Lemon, 0.38f),
                FloatingStar(0.90f, 0.71f, 4.5f, CialloPalette.Coral, 0.40f),
                FloatingStar(0.74f, 0.88f, 5.5f, CialloPalette.Blue, 0.16f),
                FloatingStar(0.18f, 0.90f, 5.0f, CialloPalette.Lemon, 0.44f),
            )
            stars.forEach { star ->
                drawStar(
                    center = Offset(star.x * width, star.y * height),
                    radius = star.radius * unit,
                    color = star.color.copy(alpha = star.alpha),
                )
            }

            val sparkles = listOf(
                FloatingStar(0.18f, 0.12f, 3.0f, Color.White, 0.7f),
                FloatingStar(0.72f, 0.075f, 3.4f, Color.White, 0.75f),
                FloatingStar(0.23f, 0.50f, 2.6f, Color.White, 0.55f),
                FloatingStar(0.82f, 0.57f, 3.1f, Color.White, 0.6f),
                FloatingStar(0.10f, 0.78f, 2.8f, Color.White, 0.55f),
            )
            sparkles.forEach { sparkle ->
                drawSparkle(
                    center = Offset(sparkle.x * width, sparkle.y * height),
                    radius = sparkle.radius * unit,
                    color = sparkle.color.copy(alpha = sparkle.alpha),
                )
            }

            val bubbles = listOf(
                Triple(0.88f, 0.36f, 10f),
                Triple(0.08f, 0.21f, 7f),
                Triple(0.94f, 0.84f, 13f),
                Triple(0.12f, 0.72f, 9f),
                Triple(0.62f, 0.96f, 6f),
            )
            bubbles.forEach { (x, y, radius) ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.46f),
                    radius = radius * unit,
                    center = Offset(x * width, y * height),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                )
            }
        }
        content()
    }
}

private data class FloatingStar(
    val x: Float,
    val y: Float,
    val radius: Float,
    val color: Color,
    val alpha: Float,
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStar(
    center: Offset,
    radius: Float,
    color: Color,
) {
    val path = Path()
    val inner = radius * 0.46f
    for (index in 0 until 10) {
        val pointRadius = if (index % 2 == 0) radius else inner
        val angle = -PI / 2 + index * PI / 5
        val x = center.x + cos(angle).toFloat() * pointRadius
        val y = center.y + sin(angle).toFloat() * pointRadius
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path = path, color = color)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSparkle(
    center: Offset,
    radius: Float,
    color: Color,
) {
    val path = Path()
    path.moveTo(center.x, center.y - radius)
    path.quadraticBezierTo(
        center.x + radius * 0.16f,
        center.y - radius * 0.16f,
        center.x + radius,
        center.y,
    )
    path.quadraticBezierTo(
        center.x + radius * 0.16f,
        center.y + radius * 0.16f,
        center.x,
        center.y + radius,
    )
    path.quadraticBezierTo(
        center.x - radius * 0.16f,
        center.y + radius * 0.16f,
        center.x - radius,
        center.y,
    )
    path.quadraticBezierTo(
        center.x - radius * 0.16f,
        center.y - radius * 0.16f,
        center.x,
        center.y - radius,
    )
    path.close()
    drawPath(path = path, color = color)
}

@Composable
fun CialloTopBar(
    accessibilityEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark()
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name_short),
                style = MaterialTheme.typography.titleLarge,
                color = CialloPalette.Navy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.brand_tagline_short),
                style = MaterialTheme.typography.labelSmall,
                color = CialloPalette.Blue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        ServiceStatusPill(accessibilityEnabled = accessibilityEnabled)
    }
}

@Composable
fun BrandMark(size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.30f)
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Color.White)
            .border(1.5.dp, CialloPalette.Navy.copy(alpha = 0.18f), shape),
    )
}

@Composable
fun ServiceStatusPill(
    accessibilityEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = if (accessibilityEnabled) CialloPalette.Blue else CialloPalette.Coral
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.78f))
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Text(
            text = if (accessibilityEnabled) "守护中" else "未开启",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif),
            color = if (accessibilityEnabled) CialloPalette.BlueDeep else CialloPalette.CoralDeep,
            maxLines = 1,
        )
    }
}

@Composable
fun PixelTag(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(1.dp, CialloPalette.Navy.copy(alpha = 0.16f), shape)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Composable
fun CialloCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = CialloPalette.Navy.copy(alpha = 0.11f),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Card(
        modifier = modifier.then(clickableModifier),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.5.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun CialloSectionHeader(
    index: String,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CialloPalette.Lemon)
                .border(1.5.dp, CialloPalette.Navy.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = index,
                style = MaterialTheme.typography.labelSmall,
                color = CialloPalette.Navy,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = CialloPalette.Navy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = CialloPalette.LemonDeep.copy(alpha = 0.9f),
                    modifier = Modifier.size(14.dp),
                )
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun CialloIconTile(
    icon: ImageVector,
    tint: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.30f))
            .background(containerColor)
            .border(1.5.dp, CialloPalette.Navy.copy(alpha = 0.10f), RoundedCornerShape(size * 0.30f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.50f),
        )
    }
}

@Composable
fun CialloPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = CialloPalette.Blue,
    contentColor: Color = Color.White,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = CialloPalette.Ice,
            disabledContentColor = CialloPalette.Muted,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun CialloGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = CialloPalette.Navy,
        )
    }
}

@Composable
fun CialloDialog(
    onDismissRequest: () -> Unit,
    icon: ImageVector,
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String = "取消",
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.5.dp, CialloPalette.Navy.copy(alpha = 0.12f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                CialloIconTile(
                    icon = icon,
                    tint = CialloPalette.CoralDeep,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    size = 48.dp,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = CialloPalette.Navy,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CialloGhostButton(
                        text = dismissLabel,
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f),
                    )
                    CialloPrimaryButton(
                        text = confirmLabel,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1.2f),
                        containerColor = CialloPalette.CoralDeep,
                    )
                }
            }
        }
    }
}

@Composable
fun CialloEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.72f))
                    .border(1.5.dp, CialloPalette.Blue.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CialloPalette.Blue,
                    modifier = Modifier.size(38.dp),
                )
            }
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = CialloPalette.Lemon,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(CialloPalette.Coral.copy(alpha = 0.8f)),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = CialloPalette.Navy,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun CialloSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = CialloPalette.Blue,
    checkedBorderColor = CialloPalette.BlueDeep,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = CialloPalette.Ice,
    uncheckedBorderColor = CialloPalette.Navy.copy(alpha = 0.18f),
)
