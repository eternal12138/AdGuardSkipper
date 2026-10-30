package com.adguardskipper.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.adguardskipper.MainActivity
import com.adguardskipper.R
import com.adguardskipper.util.PermissionUtils

/**
 * Hosts the persistent foreground notification for the accessibility service and
 * coordinates with the notification listener. Both services are bound by the
 * system in the same process, so keeping the extra system binding alive makes
 * the process less likely to be frozen by aggressive ROMs.
 */
object KeepAliveHelper {
    const val CHANNEL_ID = "guard_service"
    private const val NOTIFICATION_ID = 4201
    private const val TAG = "KeepAliveHelper"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.keep_alive_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.keep_alive_channel_description)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    fun startGuard(service: Service, todayBlockCount: Int): Boolean {
        return runCatching {
            ensureChannel(service)
            val notification = buildNotification(service, todayBlockCount)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                service.startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                service.startForeground(NOTIFICATION_ID, notification)
            }
            true
        }.onFailure { error ->
            Log.e(TAG, "Unable to start guard foreground notification", error)
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun updateGuardNotification(context: Context, todayBlockCount: Int) {
        if (!PermissionUtils.areNotificationsGranted(context)) return
        runCatching {
            ensureChannel(context)
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID,
                buildNotification(context, todayBlockCount),
            )
        }.onFailure { error ->
            Log.w(TAG, "Unable to update guard notification", error)
        }
    }

    fun stopGuard(service: Service) {
        runCatching { service.stopForeground(Service.STOP_FOREGROUND_REMOVE) }
        runCatching {
            NotificationManagerCompat.from(service).cancel(NOTIFICATION_ID)
        }
    }

    /**
     * Asks the system to rebind the notification listener if the user has
     * granted notification access. This is the accessibility -> listener wake-up
     * direction of the dual-service keep-alive.
     */
    fun requestNotificationListenerRebind(context: Context) {
        if (!PermissionUtils.isNotificationListenerEnabled(context)) return
        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(context, AdGuardNotificationListenerService::class.java),
            )
        }.onFailure { error ->
            Log.d(TAG, "Notification listener rebind request ignored: ${error.message}")
        }
    }

    /**
     * Listener -> accessibility wake-up direction. The listener holds a system
     * binding and reasserts the foreground notification on the live
     * accessibility service when possible.
     */
    fun onNotificationListenerConnected() {
        val service = AdSkipAccessibilityService.currentInstance() ?: return
        Handler(Looper.getMainLooper()).post {
            service.reassertKeepAliveNotification()
        }
    }

    private fun buildNotification(context: Context, todayBlockCount: Int) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle(context.getString(R.string.keep_alive_title))
            .setContentText(
                context.getString(R.string.keep_alive_text, todayBlockCount),
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    context.getString(R.string.keep_alive_text, todayBlockCount),
                ),
            )
            .setContentIntent(mainActivityIntent(context, 1))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply {
                if (!PermissionUtils.isNotificationListenerEnabled(context)) {
                    addAction(
                        0,
                        context.getString(R.string.keep_alive_enable_listener),
                        notificationListenerSettingsIntent(context),
                    )
                }
            }
            .build()

    private fun mainActivityIntent(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun notificationListenerSettingsIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            2,
            Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/**
 * NotificationListenerService used only as a lightweight second system binding.
 * It intentionally does not read, store or forward notification content.
 */
class AdGuardNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        KeepAliveHelper.onNotificationListenerConnected()
    }
}
