package com.adguardskipper.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adguardskipper.AdGuardSkipperApp
import com.adguardskipper.BuildConfig
import com.adguardskipper.data.model.AppSettings
import com.adguardskipper.data.model.InstalledApp
import com.adguardskipper.data.model.InterceptLog
import com.adguardskipper.data.model.LocalSkipRule
import com.adguardskipper.data.model.PermissionState
import com.adguardskipper.service.AdSkipAccessibilityService
import com.adguardskipper.util.PermissionUtils
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AdGuardSkipperApp).container
    private val preferences = container.preferences
    private val appRepository = container.appRepository
    private val appContext = application.applicationContext

    val settings: StateFlow<AppSettings> = preferences.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    /** Reactive consent flag; false until the user accepts the agreement. */
    val agreementAccepted: StateFlow<Boolean> = preferences.userAgreementAccepted

    /** True once the persisted consent state has actually been read. */
    val agreementLoaded: StateFlow<Boolean> = preferences.agreementLoaded

    val versionLabel: String
        get() = "Version ${versionInfo.versionName} (Build ${versionInfo.versionCode})"

    val logs: StateFlow<List<InterceptLog>> = preferences.logs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _appsLoading = MutableStateFlow(true)
    val appsLoading: StateFlow<Boolean> = _appsLoading.asStateFlow()

    private val _thirdPartyAppCount = MutableStateFlow(0)
    val thirdPartyAppCount: StateFlow<Int> = _thirdPartyAppCount.asStateFlow()

    private val _permissions = MutableStateFlow(PermissionState())
    val permissions: StateFlow<PermissionState> = _permissions.asStateFlow()

    private val _appLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    val appLabels: StateFlow<Map<String, String>> = _appLabels.asStateFlow()

    private val _floatingBallShowing = MutableStateFlow(container.layoutInspector.isShowing)
    val floatingBallShowing: StateFlow<Boolean> = _floatingBallShowing.asStateFlow()

    private var appsJob: Job? = null

    init {
        refreshPermissions()
        refreshApps()
    }

    fun refreshPermissions() {
        container.layoutInspector.syncWithPermission()
        _permissions.value = PermissionState(
            accessibilityEnabled = PermissionUtils.isAccessibilityServiceEnabled(appContext),
            batteryOptimizationIgnored = PermissionUtils.isBatteryOptimizationIgnored(appContext),
            notificationsGranted = PermissionUtils.areNotificationsGranted(appContext),
            overlayGranted = PermissionUtils.canDrawOverlays(appContext),
            notificationListenerGranted =
                PermissionUtils.isNotificationListenerEnabled(appContext),
        )
        _floatingBallShowing.value = container.layoutInspector.isShowing
        viewModelScope.launch {
            preferences.ensureFreshCounters()
        }
    }

    /**
     * Toggles the floating layout inspector. Returns true only when the request
     * ends with the ball/panel in the expected state.
     */
    fun setFloatingBallEnabled(enabled: Boolean): Boolean {
        val success = if (enabled) {
            container.layoutInspector.show()
        } else {
            container.layoutInspector.hide()
            true
        }
        _floatingBallShowing.value = container.layoutInspector.isShowing
        return success
    }

    fun refreshFloatingBallState() {
        _floatingBallShowing.value = container.layoutInspector.isShowing
    }

    fun refreshApps(force: Boolean = false) {
        if (appsJob?.isActive == true) return
        if (!force && _installedApps.value.isNotEmpty()) {
            _appsLoading.value = false
            return
        }
        appsJob = viewModelScope.launch {
            _appsLoading.value = true
            runCatching { appRepository.loadLaunchableApps() }
                .onSuccess { apps ->
                    _installedApps.value = apps
                    _appLabels.value = apps.associate { it.packageName to it.label }
                    _thirdPartyAppCount.value = apps.count { !it.isSystemApp }
                }
            _appsLoading.value = false
        }
    }

    fun clearCustomRules() {
        AdSkipAccessibilityService.currentInstance()?.onCustomRulesCleared()
        viewModelScope.launch {
            preferences.clearCustomRules()
        }
    }

    fun setWhitelisted(packageName: String, whitelisted: Boolean) {
        viewModelScope.launch {
            preferences.setWhitelisted(packageName, whitelisted)
        }
    }

    fun setBlacklisted(packageName: String, blacklisted: Boolean) {
        viewModelScope.launch {
            preferences.setBlacklisted(packageName, blacklisted)
        }
    }

    fun setUserAgreementAccepted(accepted: Boolean) {
        viewModelScope.launch {
            preferences.setUserAgreementAccepted(accepted)
        }
    }

    /**
     * Revokes consent immediately. The service is halted synchronously so no
     * click can happen between the tap and the DataStore update.
     */
    fun revokeAgreement() {
        AdSkipAccessibilityService.currentInstance()?.onAgreementRevoked()
        setFloatingBallEnabled(false)
        viewModelScope.launch {
            preferences.setUserAgreementAccepted(false)
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            preferences.clearLogs()
        }
    }

    fun labelFor(packageName: String): String {
        if (packageName.isBlank()) return packageName
        return _appLabels.value[packageName]
            ?: runCatching { appRepository.labelForPackage(packageName) }
                .getOrDefault(packageName)
    }

    /**
     * Builds a local, human-readable JSON snapshot with the current custom
     * rules, recent intercept logs and basic device/app info. The same text is
     * also persisted under files/debug_exports for later retrieval.
     */
    suspend fun exportDebugInfo(): String = withContext(Dispatchers.IO) {
        val rules = preferences.customRules.first()
        val logs = preferences.logs.first()
        val info = versionInfo
        val root = JSONObject().apply {
            put("schemaVersion", 1)
            put("exportedAt", Instant.now().toString())
            put(
                "app",
                JSONObject().apply {
                    put("packageName", appContext.packageName)
                    put("versionName", info.versionName)
                    put("versionCode", info.versionCode)
                    put("buildType", if (BuildConfig.DEBUG) "debug" else "release")
                },
            )
            put(
                "device",
                JSONObject().apply {
                    put("androidVersion", Build.VERSION.RELEASE)
                    put("apiLevel", Build.VERSION.SDK_INT)
                    put("manufacturer", Build.MANUFACTURER)
                    put("model", Build.MODEL)
                },
            )
            put("customRules", encodeRules(rules))
            put("interceptLogs", encodeLogs(logs))
        }
        val json = root.toString(2)

        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .format(LocalDateTime.now())
        val exportDir = File(appContext.filesDir, "debug_exports").apply { mkdirs() }
        File(exportDir, "ciallo_debug_$stamp.json").writeText(json)
        json
    }

    private fun encodeRules(rules: Map<String, LocalSkipRule>): JSONObject {
        val root = JSONObject()
        rules.toSortedMap().forEach { (packageName, rule) ->
            root.put(
                packageName,
                JSONObject().apply {
                    put(
                        "viewIds",
                        JSONArray().apply {
                            rule.viewIds.forEach { item ->
                                put(
                                    JSONObject().apply {
                                        put("viewId", item.viewId)
                                        put("activityName", item.activityName)
                                        put("globalInAppRule", item.globalInAppRule)
                                        put("createdAt", item.createdAt)
                                    },
                                )
                            }
                        },
                    )
                    put(
                        "texts",
                        JSONArray().apply {
                            rule.texts.forEach { item ->
                                put(
                                    JSONObject().apply {
                                        put("text", item.text)
                                        put("activityName", item.activityName)
                                        put("globalInAppRule", item.globalInAppRule)
                                        put("createdAt", item.createdAt)
                                    },
                                )
                            }
                        },
                    )
                    put(
                        "coordinates",
                        JSONArray().apply {
                            rule.coordinates.forEach { item ->
                                put(
                                    JSONObject().apply {
                                        put("x", item.point.x.toDouble())
                                        put("y", item.point.y.toDouble())
                                        put("activityName", item.activityName)
                                        put("globalInAppRule", item.globalInAppRule)
                                        put("createdAt", item.createdAt)
                                    },
                                )
                            }
                        },
                    )
                },
            )
        }
        return root
    }

    private fun encodeLogs(logs: List<InterceptLog>): JSONArray {
        return JSONArray().apply {
            logs.forEach { log ->
                put(
                    JSONObject().apply {
                        put("id", log.id)
                        put("timestamp", log.timestamp)
                        put(
                            "time",
                            runCatching { Instant.ofEpochMilli(log.timestamp).toString() }
                                .getOrDefault(""),
                        )
                        put("type", log.type.storageKey)
                        put("packageName", log.packageName)
                        put("sourcePackage", log.sourcePackage ?: JSONObject.NULL)
                        put("trigger", log.trigger)
                        put("detail", log.detail)
                    },
                )
            }
        }
    }

    private fun readVersionInfo(): VersionInfo {
        val fallback = VersionInfo(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toLong(),
        )
        return runCatching {
            val packageInfo = appContext.packageManager
                .getPackageInfo(appContext.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            VersionInfo(
                versionName = packageInfo.versionName ?: BuildConfig.VERSION_NAME,
                versionCode = code,
            )
        }.getOrDefault(fallback)
    }

    private val versionInfo: VersionInfo by lazy { readVersionInfo() }

    private data class VersionInfo(
        val versionName: String,
        val versionCode: Long,
    )

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
