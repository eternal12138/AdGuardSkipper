package com.adguardskipper.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adguardskipper.ui.components.CialloBackdrop
import com.adguardskipper.ui.components.CialloTopBar
import com.adguardskipper.ui.screens.AboutScreen
import com.adguardskipper.ui.screens.DashboardScreen
import com.adguardskipper.ui.screens.ListsScreen
import com.adguardskipper.ui.screens.LogsScreen
import com.adguardskipper.ui.theme.CialloPalette
import com.adguardskipper.util.SystemIntents

private enum class MainTab(
    val label: String,
    val icon: ImageVector,
) {
    DASHBOARD("主页", Icons.Outlined.Dashboard),
    LISTS("名单", Icons.Outlined.Shield),
    LOGS("日志", Icons.Outlined.History),
    ABOUT("关于", Icons.Filled.Info),
}

@Composable
fun AdGuardSkipperRoot(viewModel: MainViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val apps by viewModel.installedApps.collectAsStateWithLifecycle()
    val appsLoading by viewModel.appsLoading.collectAsStateWithLifecycle()
    val thirdPartyAppCount by viewModel.thirdPartyAppCount.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val labels by viewModel.appLabels.collectAsStateWithLifecycle()
    val floatingBallEnabled by viewModel.floatingBallShowing.collectAsStateWithLifecycle()

    var mainTab by rememberSaveable { mutableIntStateOf(0) }
    var listTab by rememberSaveable { mutableIntStateOf(0) }
    var notificationRequested by rememberSaveable { mutableStateOf(false) }
    var pendingOverlayEnable by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(permissions.overlayGranted, permissions.accessibilityEnabled) {
        if (pendingOverlayEnable &&
            permissions.overlayGranted &&
            permissions.accessibilityEnabled
        ) {
            pendingOverlayEnable = false
            if (!viewModel.setFloatingBallEnabled(true)) {
                Toast.makeText(
                    context,
                    "悬浮球启动失败，请检查系统悬浮窗设置",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshPermissions()
    }

    val openNotificationPermissionOrSettings: () -> Unit = {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !permissions.notificationsGranted &&
            !notificationRequested
        ) {
            notificationRequested = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            SystemIntents.openNotificationSettings(context)
        }
    }

    CialloBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                CialloTopBar(accessibilityEnabled = permissions.accessibilityEnabled)
            },
            bottomBar = {
                NavigationBar(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.White.copy(alpha = 0.92f))
                        .border(
                            BorderStroke(1.5.dp, CialloPalette.Navy.copy(alpha = 0.10f)),
                            RoundedCornerShape(26.dp),
                        ),
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                ) {
                    MainTab.entries.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = mainTab == index,
                            onClick = { mainTab = index },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = CialloPalette.BlueDeep,
                                indicatorColor = CialloPalette.Blue,
                                unselectedIconColor = CialloPalette.Navy.copy(alpha = 0.55f),
                                unselectedTextColor = CialloPalette.Navy.copy(alpha = 0.55f),
                            ),
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when (MainTab.entries[mainTab]) {
                    MainTab.DASHBOARD -> DashboardScreen(
                        settings = settings,
                        permissions = permissions,
                        onAccessibilitySettings = {
                            SystemIntents.openAccessibilitySettings(context)
                        },
                        onBatterySettings = {
                            SystemIntents.requestIgnoreBatteryOptimizations(context)
                        },
                        onNotificationSettings = openNotificationPermissionOrSettings,
                        onNotificationListenerSettings = {
                            SystemIntents.openNotificationListenerSettings(context)
                        },
                        floatingBallEnabled = floatingBallEnabled,
                        onClearCustomRules = {
                            viewModel.clearCustomRules()
                            Toast.makeText(
                                context,
                                "已清空所有自定义规则",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        onToggleFloatingBall = { enabled ->
                            when {
                                !enabled -> viewModel.setFloatingBallEnabled(false)

                                !permissions.overlayGranted -> {
                                    pendingOverlayEnable = true
                                    SystemIntents.openOverlayPermissionSettings(context)
                                    Toast.makeText(
                                        context,
                                        "请授予“显示在其他应用上层”权限",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }

                                !permissions.accessibilityEnabled -> {
                                    pendingOverlayEnable = true
                                    Toast.makeText(
                                        context,
                                        "请先开启无障碍服务，再启用布局抓取",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    SystemIntents.openAccessibilitySettings(context)
                                }

                                else -> {
                                    if (!viewModel.setFloatingBallEnabled(true)) {
                                        Toast.makeText(
                                            context,
                                            "悬浮球启动失败，请检查系统悬浮窗设置",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            }
                        },
                        onOpenWhitelist = {
                            listTab = 0
                            mainTab = MainTab.LISTS.ordinal
                        },
                        onOpenBlacklist = {
                            listTab = 1
                            mainTab = MainTab.LISTS.ordinal
                        },
                    )

                    MainTab.LISTS -> ListsScreen(
                        settings = settings,
                        apps = apps,
                        loading = appsLoading,
                        thirdPartyAppCount = thirdPartyAppCount,
                        selectedTab = listTab,
                        onTabSelected = { listTab = it },
                        onSetWhitelisted = viewModel::setWhitelisted,
                        onSetBlacklisted = viewModel::setBlacklisted,
                        onRefreshApps = { viewModel.refreshApps(force = true) },
                        onOpenAppSettings = {
                            SystemIntents.openAppDetailsSettings(context)
                        },
                    )

                    MainTab.LOGS -> LogsScreen(
                        logs = logs,
                        labels = labels,
                        onClearLogs = viewModel::clearLogs,
                    )

                    MainTab.ABOUT -> AboutScreen(
                        versionLabel = viewModel.versionLabel,
                        onRevokeAgreement = viewModel::revokeAgreement,
                        buildDebugExport = viewModel::exportDebugInfo,
                    )
                }
            }
        }
    }
}
