package com.adguardskipper.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

object SystemIntents {
    fun openAccessibilitySettings(context: Context) {
        launch(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun openAppDetailsSettings(context: Context) {
        launch(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            },
            errorMessage = "无法打开应用详情设置",
        )
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        val request = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
        val launched = runCatching {
            context.startActivity(request.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
        if (!launched) {
            launch(
                context,
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                errorMessage = "无法打开电池优化设置",
            )
        }
    }

    fun openNotificationSettings(context: Context) {
        val notificationIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(notificationIntent); true }.getOrDefault(false)) return
        launch(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            },
            errorMessage = "无法打开通知设置",
        )
    }

    fun openOverlayPermissionSettings(context: Context) {
        val appSpecific = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
        if (runCatching {
                context.startActivity(appSpecific.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
        ) {
            return
        }
        launch(
            context,
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            errorMessage = "无法打开悬浮窗权限设置",
        )
    }

    fun openNotificationListenerSettings(context: Context) {
        launch(
            context,
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            errorMessage = "无法打开通知使用权设置",
        )
    }

    private fun launch(
        context: Context,
        intent: Intent,
        errorMessage: String = "无法打开系统设置",
    ) {
        val handled = runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
        if (!handled) {
            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }
}
