package com.adguardskipper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adguardskipper.data.model.InterceptLog
import com.adguardskipper.data.model.InterceptLogType
import com.adguardskipper.ui.components.CialloCard
import com.adguardskipper.ui.components.CialloDialog
import com.adguardskipper.ui.components.CialloEmptyState
import com.adguardskipper.ui.components.CialloIconTile
import com.adguardskipper.ui.components.PixelTag
import com.adguardskipper.ui.theme.CialloPalette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val logTimeFormatter: DateTimeFormatter = DateTimeFormatter
    .ofPattern("MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())

@Composable
fun LogsScreen(
    logs: List<InterceptLog>,
    labels: Map<String, String>,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showClearDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 14.dp, top = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CialloIconTile(
                icon = Icons.Outlined.History,
                tint = CialloPalette.Blue,
                containerColor = CialloPalette.Ice,
                size = 44.dp,
            )
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "拦截日志",
                        style = MaterialTheme.typography.titleLarge,
                        color = CialloPalette.Navy,
                    )
                    Spacer(Modifier.width(6.dp))
                    PixelTag(
                        text = logs.size.toString(),
                        containerColor = CialloPalette.Lemon,
                        contentColor = CialloPalette.Navy,
                    )
                }
                Text(
                    text = if (logs.isEmpty()) "尚无记录" else "最近 ${logs.size} 条 · 仅保存在本机",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (logs.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color.White.copy(alpha = 0.72f))
                        .border(
                            1.dp,
                            CialloPalette.Coral.copy(alpha = 0.20f),
                            RoundedCornerShape(13.dp),
                        )
                        .clickable(role = Role.Button) { showClearDialog = true }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = CialloPalette.CoralDeep,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = "清空",
                        style = MaterialTheme.typography.labelMedium,
                        color = CialloPalette.CoralDeep,
                    )
                }
            }
        }

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                CialloEmptyState(
                    icon = Icons.Outlined.History,
                    title = "还没有拦截记录",
                    message = "开启无障碍服务后，跳过广告、拒绝弹窗和拉回强跳都会记录在这里。",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = logs, key = { it.id }) { log ->
                    LogEntryCard(
                        log = log,
                        packageLabel = labels[log.packageName] ?: log.packageName,
                        sourceLabel = log.sourcePackage?.let { source ->
                            labels[source] ?: source
                        },
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        CialloDialog(
            onDismissRequest = { showClearDialog = false },
            icon = Icons.Outlined.DeleteOutline,
            title = "清空全部日志？",
            message = "此操作不可撤销，今日统计次数不会受到影响。",
            confirmLabel = "清空",
            onConfirm = {
                onClearLogs()
                showClearDialog = false
            },
        )
    }
}

@Composable
private fun LogEntryCard(
    log: InterceptLog,
    packageLabel: String,
    sourceLabel: String?,
) {
    val accent = logAccent(log.type)
    val icon = logIcon(log.type)
    val tagContent = if (log.type == InterceptLogType.BLOCK_POPUP) {
        CialloPalette.Navy
    } else {
        Color.White
    }

    CialloCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = accent.copy(alpha = 0.18f),
        contentPadding = PaddingValues(13.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            CialloIconTile(
                icon = icon,
                tint = accent,
                containerColor = accent.copy(alpha = 0.11f),
                size = 42.dp,
            )
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelTag(
                        text = log.type.displayName,
                        containerColor = accent,
                        contentColor = tagContent,
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = logTimeFormatter.format(Instant.ofEpochMilli(log.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = CialloPalette.Muted,
                        modifier = Modifier.weight(1f),
                    )
                    PixelTag(
                        text = "LOCAL",
                        containerColor = Color.White.copy(alpha = 0.8f),
                        contentColor = CialloPalette.Muted,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = packageLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = CialloPalette.Navy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = log.packageName,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif),
                    color = CialloPalette.Muted.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!sourceLabel.isNullOrBlank() && sourceLabel != packageLabel) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "来源：$sourceLabel（${log.sourcePackage}）",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif),
                        color = CialloPalette.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = log.trigger,
                    style = MaterialTheme.typography.bodySmall,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                )
                if (log.detail.isNotBlank()) {
                    Text(
                        text = log.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun logAccent(type: InterceptLogType): Color {
    return when (type) {
        InterceptLogType.SKIP_ADS -> CialloPalette.Blue
        InterceptLogType.BLOCK_JUMP -> CialloPalette.CoralDeep
        InterceptLogType.BLOCK_POPUP -> CialloPalette.LemonDeep
    }
}

private fun logIcon(type: InterceptLogType): ImageVector {
    return when (type) {
        InterceptLogType.SKIP_ADS -> Icons.Outlined.SkipNext
        InterceptLogType.BLOCK_JUMP -> Icons.Outlined.Block
        InterceptLogType.BLOCK_POPUP -> Icons.Outlined.Shield
    }
}
