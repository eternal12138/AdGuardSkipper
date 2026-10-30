package com.adguardskipper.data.model

import java.time.LocalDate

/** The kind of event recorded by the accessibility service. */
enum class InterceptLogType(val storageKey: String, val displayName: String) {
    SKIP_ADS("skip_ads", "跳过广告"),
    BLOCK_JUMP("block_jump", "拦截强跳"),
    BLOCK_POPUP("block_popup", "拒绝弹窗"),
    ;

    companion object {
        fun fromStorage(value: String?): InterceptLogType =
            entries.firstOrNull { it.storageKey == value } ?: SKIP_ADS
    }
}

/**
 * A single, user-visible interception event. Logs are deliberately small and
 * contain no screen text beyond the matched rule label.
 */
data class InterceptLog(
    val id: String,
    val timestamp: Long,
    val type: InterceptLogType,
    val packageName: String,
    val sourcePackage: String? = null,
    val trigger: String,
    val detail: String = "",
)

data class AppSettings(
    val whitelist: Set<String> = emptySet(),
    val blacklist: Set<String> = DefaultData.BLACKLIST,
    val skipCount: Int = 0,
    val blockCount: Int = 0,
    val customRuleCount: Int = 0,
    val statsDate: String = DefaultData.todayKey(),
    /**
     * Hard consent gate. Until this is true neither the UI nor the
     * accessibility service may scan, click or intercept anything.
     */
    val isUserAgreementAccepted: Boolean = false,
)

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
)

data class PermissionState(
    val accessibilityEnabled: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val notificationsGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val notificationListenerGranted: Boolean = false,
)

/** Normalized screen coordinate, where 0,0 is the top-left and 1,1 the bottom-right. */
data class NormalizedPoint(
    val x: Float,
    val y: Float,
)

/** A package-specific skip rule loaded from assets/default_rules.json. */
data class SkipRule(
    val packageName: String,
    val viewIds: List<String> = emptyList(),
    val texts: List<String> = emptyList(),
    val points: List<NormalizedPoint> = emptyList(),
    val note: String = "",
)

data class LocalViewIdRule(
    val viewId: String,
    val activityName: String = "",
    val globalInAppRule: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

data class LocalCoordinateRule(
    val point: NormalizedPoint,
    val activityName: String = "",
    val globalInAppRule: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

data class LocalTextRule(
    val text: String,
    val activityName: String = "",
    val globalInAppRule: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * A rule captured from the floating layout inspector. Coordinates are stored as
 * normalized screen ratios so they continue to work across resolutions.
 */
data class LocalSkipRule(
    val viewIds: List<LocalViewIdRule> = emptyList(),
    val coordinates: List<LocalCoordinateRule> = emptyList(),
    val texts: List<LocalTextRule> = emptyList(),
) {
    val size: Int
        get() = viewIds.size + coordinates.size + texts.size
}

object DefaultData {
    /**
     * Apps commonly used for splash-screen "shake to jump" advertisements.
     * Kept in sync with the editable blacklist in DataStore.
     */
    val BLACKLIST: Set<String> = linkedSetOf(
        "com.taobao.taobao",
        "com.jingdong.app.mall",
        "com.xunmeng.pinduoduo",
        "com.sankuai.meituan",
        "com.ss.android.ugc.aweme",
        "com.ss.android.ugc.aweme.lite",
        "com.smile.gifmaker",
        "com.kuaishou.nebula",
        "com.taobao.idlefish",
        "com.tmall.wireless",
        "com.achievo.vipshop",
        "com.kaola",
    )

    fun todayKey(): String = LocalDate.now().toString()
}
