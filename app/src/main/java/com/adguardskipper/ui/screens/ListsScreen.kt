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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adguardskipper.data.model.AppSettings
import com.adguardskipper.data.model.InstalledApp
import com.adguardskipper.ui.AppIcon
import com.adguardskipper.ui.components.CialloCard
import com.adguardskipper.ui.components.CialloEmptyState
import com.adguardskipper.ui.components.CialloIconTile
import com.adguardskipper.ui.components.CialloSwitchColors
import com.adguardskipper.ui.components.PixelTag
import com.adguardskipper.ui.theme.CialloPalette

@Composable
fun ListsScreen(
    settings: AppSettings,
    apps: List<InstalledApp>,
    loading: Boolean,
    thirdPartyAppCount: Int,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onSetWhitelisted: (String, Boolean) -> Unit,
    onSetBlacklisted: (String, Boolean) -> Unit,
    onRefreshApps: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var includeSystemApps by rememberSaveable { mutableStateOf(false) }
    var onlyListed by rememberSaveable { mutableStateOf(false) }

    val normalizedQuery = query.trim()
    val listName = if (selectedTab == 0) "白名单" else "黑名单"
    val accent = if (selectedTab == 0) CialloPalette.Blue else CialloPalette.CoralDeep
    val activePackages = if (selectedTab == 0) settings.whitelist else settings.blacklist

    val baseFiltered = remember(apps, normalizedQuery, includeSystemApps) {
        apps.filter { app ->
            (includeSystemApps || !app.isSystemApp) &&
                (
                    normalizedQuery.isEmpty() ||
                        app.label.contains(normalizedQuery, ignoreCase = true) ||
                        app.packageName.contains(normalizedQuery, ignoreCase = true)
                    )
        }
    }

    // The active list always comes first: whitelist members on the whitelist
    // tab, blacklist members on the blacklist tab. The incoming list is already
    // sorted by label, and this sort is stable, so groups keep A-Z order.
    val filtered = remember(baseFiltered, activePackages, onlyListed) {
        val scoped = if (onlyListed) {
            baseFiltered.filter { it.packageName in activePackages }
        } else {
            baseFiltered
        }
        scoped.sortedByDescending { it.packageName in activePackages }
    }
    val pinnedCount = remember(filtered, activePackages) {
        filtered.count { it.packageName in activePackages }
    }
    val hasOtherApps = pinnedCount < filtered.size
    val listedCandidateCount = remember(baseFiltered, activePackages) {
        baseFiltered.count { it.packageName in activePackages }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ListTabSwitcher(
            selectedTab = selectedTab,
            whitelistCount = settings.whitelist.size,
            blacklistCount = settings.blacklist.size,
            onTabSelected = onTabSelected,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 4.dp),
        )

        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            CialloCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = Color.White.copy(alpha = 0.62f),
                borderColor = CialloPalette.Blue.copy(alpha = 0.12f),
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 11.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(23.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CialloPalette.Lemon),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (selectedTab == 0) "W" else "B",
                            style = MaterialTheme.typography.labelSmall,
                            color = CialloPalette.Navy,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    Text(
                        text = if (selectedTab == 0) {
                            "开启开关后，该应用不再参与开屏扫描与应用内弹窗扫描。"
                        } else {
                            "开屏保护期内，若前台突然切换到这些应用，将自动执行返回。系统弹窗防线始终启用。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!loading && thirdPartyAppCount < 5) {
                Spacer(Modifier.height(9.dp))
                IncompleteAppListCard(onClick = onOpenAppSettings)
            }
            Spacer(Modifier.height(11.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "搜索应用名称或包名",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "清除搜索",
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CialloPalette.Blue,
                    unfocusedBorderColor = CialloPalette.Navy.copy(alpha = 0.12f),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White.copy(alpha = 0.78f),
                    cursorColor = CialloPalette.Blue,
                    focusedLeadingIconColor = CialloPalette.Blue,
                    unfocusedLeadingIconColor = CialloPalette.Muted,
                    focusedPlaceholderColor = CialloPalette.Muted,
                    unfocusedPlaceholderColor = CialloPalette.Muted,
                ),
            )
            Spacer(Modifier.height(9.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ListScopeDropdown(
                    modifier = Modifier.weight(1f),
                    listName = listName,
                    onlyListed = onlyListed,
                    accent = accent,
                    totalCount = baseFiltered.size,
                    listedCount = listedCandidateCount,
                    onSelect = { onlyListed = it },
                )
                Spacer(Modifier.width(9.dp))
                RefreshPill(onClick = onRefreshApps)
            }
            Spacer(Modifier.height(9.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterPill(
                    text = "显示系统应用",
                    selected = includeSystemApps,
                    onClick = { includeSystemApps = !includeSystemApps },
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "${filtered.size} 个应用",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif),
                    color = CialloPalette.Muted,
                    modifier = Modifier.weight(1f),
                )
                if (!onlyListed && pinnedCount > 0 && hasOtherApps) {
                    PinnedHint(text = "$listName 优先", accent = accent)
                }
            }
        }

        when {
            loading && apps.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = CialloPalette.Blue,
                            trackColor = CialloPalette.Ice,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "正在读取应用列表…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CialloPalette.Muted,
                        )
                    }
                }
            }

            filtered.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CialloEmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = when {
                            apps.isEmpty() -> "未能读取应用列表"
                            onlyListed -> "当前${listName}还没有匹配应用"
                            else -> "没有匹配的应用"
                        },
                        message = when {
                            apps.isEmpty() ->
                                "请前往系统应用详情，开启“读取应用列表”权限后刷新。"

                            onlyListed && normalizedQuery.isNotEmpty() ->
                                "换个关键词，或切换回“全部应用”查看。"

                            onlyListed ->
                                "切换到“全部应用”，打开应用右侧开关即可加入${listName}。"

                            else -> "换个关键词，或勾选“显示系统应用”。"
                        },
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = filtered,
                        key = { it.packageName },
                    ) { app ->
                        AppToggleRow(
                            app = app,
                            checked = app.packageName in activePackages,
                            accent = accent,
                            onCheckedChange = { enabled ->
                                if (selectedTab == 0) {
                                    onSetWhitelisted(app.packageName, enabled)
                                } else {
                                    onSetBlacklisted(app.packageName, enabled)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PinnedHint(text: String, accent: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.09f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.PushPin,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif),
            color = accent,
        )
    }
}

@Composable
private fun ListScopeDropdown(
    listName: String,
    onlyListed: Boolean,
    accent: Color,
    totalCount: Int,
    listedCount: Int,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(13.dp)

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (expanded) Color.White else Color.White.copy(alpha = 0.78f))
                .border(
                    width = 1.dp,
                    color = when {
                        expanded -> accent.copy(alpha = 0.45f)
                        onlyListed -> accent.copy(alpha = 0.28f)
                        else -> CialloPalette.Navy.copy(alpha = 0.10f)
                    },
                    shape = shape,
                )
                .clickable(role = Role.DropdownList) { expanded = true }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.FilterAlt,
                contentDescription = null,
                tint = if (onlyListed || expanded) accent else CialloPalette.Muted,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (onlyListed) "仅$listName" else "全部应用",
                style = MaterialTheme.typography.labelMedium,
                color = if (onlyListed) accent else CialloPalette.Navy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded) "收起展示范围" else "展开展示范围",
                tint = if (onlyListed) accent else CialloPalette.Muted,
                modifier = Modifier
                    .size(17.dp)
                    .rotate(if (expanded) 180f else 0f),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(236.dp),
        ) {
            ScopeMenuItem(
                label = "全部应用",
                count = totalCount,
                selected = !onlyListed,
                accent = accent,
                onClick = {
                    expanded = false
                    onSelect(false)
                },
            )
            ScopeMenuItem(
                label = "仅已加入$listName",
                count = listedCount,
                selected = onlyListed,
                accent = accent,
                onClick = {
                    expanded = false
                    onSelect(true)
                },
            )
        }
    }
}

@Composable
private fun ScopeMenuItem(
    label: String,
    count: Int,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) accent else CialloPalette.Navy,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        },
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = if (selected) accent else Color.Transparent,
                modifier = Modifier.size(18.dp),
            )
        },
        trailingIcon = {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif),
                color = CialloPalette.Muted,
            )
        },
    )
}

@Composable
private fun RefreshPill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(13.dp))
            .background(Color.White.copy(alpha = 0.72f))
            .border(
                1.dp,
                CialloPalette.Navy.copy(alpha = 0.10f),
                RoundedCornerShape(13.dp),
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Refresh,
            contentDescription = null,
            tint = CialloPalette.Blue,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = "刷新",
            style = MaterialTheme.typography.labelMedium,
            color = CialloPalette.BlueDeep,
        )
    }
}

@Composable
private fun ListTabSwitcher(
    selectedTab: Int,
    whitelistCount: Int,
    blacklistCount: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.66f))
            .border(
                1.5.dp,
                CialloPalette.Navy.copy(alpha = 0.10f),
                RoundedCornerShape(20.dp),
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ListTab(
            modifier = Modifier.weight(1f),
            selected = selectedTab == 0,
            label = "应用白名单",
            count = whitelistCount,
            onClick = { onTabSelected(0) },
        )
        ListTab(
            modifier = Modifier.weight(1f),
            selected = selectedTab == 1,
            label = "强跳黑名单",
            count = blacklistCount,
            onClick = { onTabSelected(1) },
        )
    }
}

@Composable
private fun ListTab(
    modifier: Modifier,
    selected: Boolean,
    label: String,
    count: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) CialloPalette.Blue else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else CialloPalette.Navy.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    if (selected) {
                        Color.White.copy(alpha = 0.24f)
                    } else {
                        CialloPalette.Ice
                    },
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) Color.White else CialloPalette.BlueDeep,
            )
        }
    }
}

@Composable
private fun FilterPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) CialloPalette.Lemon else Color.White.copy(alpha = 0.72f))
            .border(
                1.dp,
                if (selected) {
                    CialloPalette.Navy.copy(alpha = 0.16f)
                } else {
                    CialloPalette.Navy.copy(alpha = 0.10f)
                },
                RoundedCornerShape(13.dp),
            )
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (selected) CialloPalette.Navy else CialloPalette.Navy.copy(alpha = 0.22f)),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = CialloPalette.Navy,
        )
    }
}

@Composable
private fun IncompleteAppListCard(onClick: () -> Unit) {
    CialloCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = Color(0xFFFFF0F2),
        borderColor = CialloPalette.Coral.copy(alpha = 0.25f),
        contentPadding = PaddingValues(13.dp),
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = CialloPalette.CoralDeep,
                modifier = Modifier.size(21.dp),
            )
            Column {
                Text(
                    text = "未能读取到完整应用列表",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF7A1C2B),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "请点击此处前往【应用权限设置】，开启“读取应用列表”权限后返回刷新。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF7A1C2B).copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
private fun AppToggleRow(
    app: InstalledApp,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(19.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (checked) accent.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.80f))
            .border(
                1.5.dp,
                if (checked) accent.copy(alpha = 0.42f) else CialloPalette.Navy.copy(alpha = 0.08f),
                shape,
            )
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .border(
                    1.dp,
                    CialloPalette.Navy.copy(alpha = 0.10f),
                    RoundedCornerShape(14.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(packageName = app.packageName, label = app.label, size = 38.dp)
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = CialloPalette.Navy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (app.isSystemApp) {
                    Spacer(Modifier.width(6.dp))
                    PixelTag(
                        text = "SYS",
                        containerColor = CialloPalette.Ice,
                        contentColor = CialloPalette.Muted,
                    )
                }
            }
            Spacer(Modifier.height(1.dp))
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif),
                color = CialloPalette.Muted.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            PixelTag(
                text = if (checked) "ON" else "OFF",
                containerColor = if (checked) accent else CialloPalette.Cloud,
                contentColor = if (checked) Color.White else CialloPalette.Muted,
            )
            Spacer(Modifier.height(3.dp))
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = CialloSwitchColors(),
            )
        }
    }
}
