package com.adguardskipper.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adguardskipper.R
import com.adguardskipper.data.model.AppSettings
import com.adguardskipper.data.model.PermissionState
import com.adguardskipper.ui.components.CialloCard
import com.adguardskipper.ui.components.CialloIconTile
import com.adguardskipper.ui.components.CialloSectionHeader
import com.adguardskipper.ui.components.CialloSwitchColors
import com.adguardskipper.ui.components.PixelTag
import com.adguardskipper.ui.theme.CialloPalette

@Composable
fun DashboardScreen(
    settings: AppSettings,
    permissions: PermissionState,
    onAccessibilitySettings: () -> Unit,
    onBatterySettings: () -> Unit,
    onNotificationSettings: () -> Unit,
    onNotificationListenerSettings: () -> Unit,
    floatingBallEnabled: Boolean,
    onToggleFloatingBall: (Boolean) -> Unit,
    onClearCustomRules: () -> Unit,
    onOpenWhitelist: () -> Unit,
    onOpenBlacklist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 18.dp, top = 4.dp, end = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        item {
            HeroCard(accessibilityEnabled = permissions.accessibilityEnabled)
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    tag = "SKIP",
                    icon = Icons.Outlined.SkipNext,
                    value = settings.skipCount.toString(),
                    label = "今日跳过广告",
                    accent = CialloPalette.Blue,
                    tint = CialloPalette.Ice,
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    tag = "BLOCK",
                    icon = Icons.Outlined.Block,
                    value = settings.blockCount.toString(),
                    label = "今日拦截强跳",
                    accent = CialloPalette.CoralDeep,
                    tint = Color(0xFFFFE3E6),
                )
            }
        }

        item {
            CialloSectionHeader(
                index = "01",
                title = "运行状态",
                subtitle = "部分系统需要手动授予以下权限",
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        item {
            PermissionCard(
                icon = Icons.Outlined.AccessibilityNew,
                title = "无障碍服务",
                description = if (permissions.accessibilityEnabled) {
                    "已开启，正在识别开屏广告与异常跳转"
                } else {
                    "未开启，无法读取界面与模拟点击"
                },
                ready = permissions.accessibilityEnabled,
                actionLabel = if (permissions.accessibilityEnabled) "管理" else "去开启",
                onAction = onAccessibilitySettings,
            )
        }
        item {
            PermissionCard(
                icon = Icons.Outlined.BatterySaver,
                title = "忽略电池优化",
                description = if (permissions.batteryOptimizationIgnored) {
                    "已忽略，系统不会限制后台无障碍服务"
                } else {
                    "建议忽略，降低服务被系统休眠的概率"
                },
                ready = permissions.batteryOptimizationIgnored,
                actionLabel = if (permissions.batteryOptimizationIgnored) "已设置" else "去设置",
                onAction = onBatterySettings,
            )
        }
        item {
            PermissionCard(
                icon = Icons.Outlined.NotificationsActive,
                title = "拦截计数通知",
                description = if (permissions.notificationsGranted) {
                    "已允许，拦截强跳后会更新一条本地通知"
                } else {
                    "未允许，Android 13 及以上需要通知权限"
                },
                ready = permissions.notificationsGranted,
                actionLabel = if (permissions.notificationsGranted) "管理" else "授权",
                onAction = onNotificationSettings,
            )
        }
        item {
            PermissionCard(
                icon = Icons.Outlined.NotificationsActive,
                title = "通知使用权",
                description = if (permissions.notificationListenerGranted) {
                    "已开启，通知监听与无障碍服务形成双重系统绑定"
                } else {
                    "建议开启，提高 HyperOS / 鸿蒙 / MagicOS 后台存活率"
                },
                ready = permissions.notificationListenerGranted,
                actionLabel = if (permissions.notificationListenerGranted) "管理" else "去授权",
                onAction = onNotificationListenerSettings,
            )
        }

        item {
            CialloSectionHeader(
                index = "02",
                title = "进阶工具",
                subtitle = "抓取顽固广告界面，补全本地跳过规则",
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        item {
            LayoutInspectorCard(
                overlayGranted = permissions.overlayGranted,
                accessibilityEnabled = permissions.accessibilityEnabled,
                enabled = floatingBallEnabled,
                customRuleCount = settings.customRuleCount,
                onToggle = onToggleFloatingBall,
            )
        }
        item {
            EmergencyClearRulesCard(
                customRuleCount = settings.customRuleCount,
                onClick = onClearCustomRules,
            )
        }

        item {
            CialloSectionHeader(
                index = "03",
                title = "名单概览",
                subtitle = "按应用精细控制扫描与强跳保护",
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                ListShortcutCard(
                    modifier = Modifier.weight(1f),
                    tag = "WHITE",
                    title = "应用白名单",
                    count = settings.whitelist.size,
                    subtitle = "不扫描这些应用",
                    onClick = onOpenWhitelist,
                )
                ListShortcutCard(
                    modifier = Modifier.weight(1f),
                    tag = "BLACK",
                    title = "强跳黑名单",
                    count = settings.blacklist.size,
                    subtitle = "开屏期禁止唤醒",
                    onClick = onOpenBlacklist,
                )
            }
        }

        item {
            HowItWorksCard()
        }
    }
}

@Composable
private fun HeroCard(accessibilityEnabled: Boolean) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFFD8F4FF),
                        Color(0xFFC4E9FF),
                        Color(0xFFFFE7F1),
                    ),
                ),
            )
            .border(1.5.dp, CialloPalette.Navy.copy(alpha = 0.12f), shape)
            .padding(19.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Star,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.60f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 10.dp, y = (-12).dp)
                .size(52.dp)
                .rotate(14f),
        )
        Icon(
            imageVector = Icons.Rounded.Star,
            contentDescription = null,
            tint = CialloPalette.Lemon.copy(alpha = 0.75f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 8.dp, y = 12.dp)
                .size(26.dp),
        )

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelTag(
                    text = "CIALLO",
                    containerColor = CialloPalette.Lemon,
                    contentColor = CialloPalette.Navy,
                )
                Spacer(Modifier.width(6.dp))
                PixelTag(
                    text = "ADS SKIPPER",
                    containerColor = Color.White.copy(alpha = 0.66f),
                    contentColor = CialloPalette.BlueDeep,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = CialloPalette.LemonDeep,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            lineHeight = 25.sp,
                        ),
                        color = CialloPalette.Navy,
                        fontWeight = FontWeight.Black,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.brand_tagline),
                        style = MaterialTheme.typography.labelSmall,
                        color = CialloPalette.Blue,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(Color.White)
                        .border(
                            2.dp,
                            Color.White.copy(alpha = 0.9f),
                            RoundedCornerShape(23.dp),
                        )
                        .border(
                            1.5.dp,
                            CialloPalette.Navy.copy(alpha = 0.12f),
                            RoundedCornerShape(23.dp),
                        )
                        .rotate(4f),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(3.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }

            Spacer(Modifier.height(15.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.72f))
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (accessibilityEnabled) {
                                    CialloPalette.Blue
                                } else {
                                    CialloPalette.Coral
                                },
                            ),
                    )
                    Text(
                        text = if (accessibilityEnabled) "开屏广告守护中" else "开屏守护未启用",
                        style = MaterialTheme.typography.labelMedium,
                        color = CialloPalette.Navy,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "免 Root · 本地识别",
                    style = MaterialTheme.typography.labelSmall,
                    color = CialloPalette.Navy.copy(alpha = 0.58f),
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    tag: String,
    icon: ImageVector,
    value: String,
    label: String,
    accent: Color,
    tint: Color,
) {
    CialloCard(
        modifier = modifier,
        contentPadding = PaddingValues(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelTag(
                text = tag,
                containerColor = accent,
                contentColor = Color.White,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(tint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(11.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace),
            color = accent,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    ready: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    CialloCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (ready) {
            CialloPalette.Navy.copy(alpha = 0.10f)
        } else {
            CialloPalette.Coral.copy(alpha = 0.24f)
        },
        contentPadding = PaddingValues(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CialloIconTile(
                icon = icon,
                tint = if (ready) CialloPalette.Blue else CialloPalette.CoralDeep,
                containerColor = if (ready) CialloPalette.Ice else Color(0xFFFFE8EB),
                size = 44.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = CialloPalette.Navy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(5.dp))
                    if (ready) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "已就绪",
                            tint = CialloPalette.Blue,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(9.dp))
            StatusAction(
                label = actionLabel,
                ready = ready,
                onClick = onAction,
            )
        }
    }
}

@Composable
private fun StatusAction(
    label: String,
    ready: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (ready) CialloPalette.Ice else CialloPalette.Blue)
            .border(
                1.dp,
                if (ready) CialloPalette.Navy.copy(alpha = 0.10f) else CialloPalette.BlueDeep,
                shape,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (ready) CialloPalette.BlueDeep else Color.White,
            maxLines = 1,
        )
    }
}

@Composable
private fun LayoutInspectorCard(
    overlayGranted: Boolean,
    accessibilityEnabled: Boolean,
    enabled: Boolean,
    customRuleCount: Int,
    onToggle: (Boolean) -> Unit,
) {
    CialloCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CialloIconTile(
                icon = Icons.Outlined.Search,
                tint = CialloPalette.Blue,
                containerColor = CialloPalette.Ice,
                size = 44.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "布局抓取悬浮球",
                        style = MaterialTheme.typography.titleMedium,
                        color = CialloPalette.Navy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(5.dp))
                    PixelTag(
                        text = "TOOL",
                        containerColor = CialloPalette.Lemon,
                        contentColor = CialloPalette.Navy,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        !overlayGranted -> "需要悬浮窗权限"
                        enabled -> "已显示在屏幕侧边"
                        else -> "未开启"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (enabled && overlayGranted) {
                        CialloPalette.Blue
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = CialloSwitchColors(),
            )
        }
        Spacer(Modifier.height(11.dp))
        Text(
            text = when {
                !overlayGranted ->
                    "首次开启会跳转系统设置授予悬浮窗权限；抓取节点还需要无障碍服务处于开启状态。"

                !accessibilityEnabled ->
                    "悬浮球可显示，但读取节点需要先开启 Ciallo～跳广 无障碍服务。"

                enabled ->
                    "拖动悬浮球到屏幕侧边。遇到广告时点击它，直接在画面上点选目标按钮，一键智能拦截；也可点“只看推荐按钮”查看少量候选。"

                else ->
                    "开启后屏幕侧边会出现悬浮球，用于抓取当前窗口的全部可见节点。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelTag(
                text = "RULE $customRuleCount",
                containerColor = CialloPalette.Ice,
                contentColor = CialloPalette.BlueDeep,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = "本地规则库（控件 + 文本 + 坐标）",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif),
                color = CialloPalette.Muted,
            )
        }
    }
}

@Composable
private fun EmergencyClearRulesCard(
    customRuleCount: Int,
    onClick: () -> Unit,
) {
    CialloCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = Color(0xFFFFF0F2),
        borderColor = CialloPalette.Coral.copy(alpha = 0.25f),
        contentPadding = PaddingValues(16.dp),
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CialloIconTile(
                icon = Icons.Outlined.DeleteSweep,
                tint = CialloPalette.CoralDeep,
                containerColor = Color.White.copy(alpha = 0.78f),
                size = 44.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "清空所有自定义规则",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF7A1C2B),
                    )
                    Spacer(Modifier.width(5.dp))
                    PixelTag(
                        text = "CLEAR",
                        containerColor = CialloPalette.Coral,
                        contentColor = Color.White,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (customRuleCount > 0) {
                        "当前有 $customRuleCount 条规则。若出现误点，点此立即清空并停止按旧坐标点击。"
                    } else {
                        "当前没有自定义规则。日常使用期不会再自动扫描通用控件。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF7A1C2B).copy(alpha = 0.78f),
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = CialloPalette.CoralDeep.copy(alpha = 0.65f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ListShortcutCard(
    modifier: Modifier,
    tag: String,
    title: String,
    count: Int,
    subtitle: String,
    onClick: () -> Unit,
) {
    CialloCard(
        modifier = modifier,
        contentPadding = PaddingValues(15.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelTag(
                text = tag,
                containerColor = if (tag == "WHITE") CialloPalette.Ice else Color(0xFFFFE2E5),
                contentColor = if (tag == "WHITE") CialloPalette.BlueDeep else CialloPalette.CoralDeep,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(CialloPalette.Cloud),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = CialloPalette.Navy.copy(alpha = 0.55f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = CialloPalette.Navy,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
            color = CialloPalette.Blue,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HowItWorksCard() {
    CialloCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = Color.White.copy(alpha = 0.62f),
        borderColor = CialloPalette.Blue.copy(alpha = 0.13f),
        contentPadding = PaddingValues(17.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CialloIconTile(
                icon = Icons.Outlined.Info,
                tint = CialloPalette.Blue,
                containerColor = CialloPalette.Ice,
                size = 36.dp,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "工作方式",
                style = MaterialTheme.typography.titleMedium,
                color = CialloPalette.Navy,
            )
            Spacer(Modifier.weight(1f))
            PixelTag(
                text = "HOW",
                containerColor = CialloPalette.Lemon,
                contentColor = CialloPalette.Navy,
            )
        }
        Spacer(Modifier.height(12.dp))
        PrincipleRow("01", "仅从桌面进入后的前 4 秒扫描开屏，日常使用保持克制。")
        PrincipleRow("02", "严格匹配“跳过”文本与广告 SDK 控件，不做几何盲扫。")
        PrincipleRow("03", "开屏 3.5 秒内异常唤醒黑名单应用时，自动返回原应用。")
    }
}

@Composable
private fun PrincipleRow(index: String, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(25.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CialloPalette.Lemon.copy(alpha = 0.72f))
                .border(
                    1.dp,
                    CialloPalette.Navy.copy(alpha = 0.10f),
                    RoundedCornerShape(8.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = index,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = CialloPalette.Navy,
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}
