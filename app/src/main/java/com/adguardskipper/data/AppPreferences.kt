package com.adguardskipper.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.adguardskipper.data.model.AppSettings
import com.adguardskipper.data.model.DefaultData
import com.adguardskipper.data.model.InterceptLog
import com.adguardskipper.data.model.InterceptLogType
import com.adguardskipper.data.model.LocalCoordinateRule
import com.adguardskipper.data.model.LocalSkipRule
import com.adguardskipper.data.model.LocalTextRule
import com.adguardskipper.data.model.LocalViewIdRule
import com.adguardskipper.data.model.NormalizedPoint
import java.io.IOException
import kotlin.math.abs
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import org.json.JSONArray
import org.json.JSONObject

private val Context.adGuardDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ad_guard_settings",
)

/**
 * Single source of truth for user rules, daily counters and the recent event
 * log. DataStore writes are atomic, so the accessibility service and the UI can
 * update the same file safely.
 */
class AppPreferences(context: Context) {
    private val dataStore = context.applicationContext.adGuardDataStore
    private val agreementScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _agreementLoaded = MutableStateFlow(false)

    /**
     * True once the DataStore snapshot has been read at least once. The UI uses
     * this to avoid flashing the consent dialog for users who already accepted.
     */
    val agreementLoaded: StateFlow<Boolean> = _agreementLoaded.asStateFlow()

    val settings: Flow<AppSettings> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { preferences -> preferences.toAppSettings() }
        .distinctUntilChanged()

    /**
     * Reactive consent flag used by Compose. The synchronous [isUserAgreementAccepted]
     * snapshot below is what the accessibility service checks on every event.
     */
    val userAgreementAccepted: StateFlow<Boolean> = settings
        .map { it.isUserAgreementAccepted }
        .onEach { _agreementLoaded.value = true }
        .stateIn(
            scope = agreementScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    /** False until the user explicitly accepts the agreement. */
    val isUserAgreementAccepted: Boolean
        get() = userAgreementAccepted.value

    suspend fun setUserAgreementAccepted(accepted: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.USER_AGREEMENT_ACCEPTED] = accepted
        }
    }

    val logs: Flow<List<InterceptLog>> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { preferences -> decodeLogs(preferences[Keys.LOGS]) }
        .distinctUntilChanged()

    /**
     * Local rules collected by the floating layout inspector. The map is package
     * name -> normalized ViewId values and normalized coordinate points.
     */
    val customRules: Flow<Map<String, LocalSkipRule>> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { preferences -> decodeCustomRules(preferences[Keys.CUSTOM_RULES]) }
        .distinctUntilChanged()

    suspend fun setWhitelisted(packageName: String, whitelisted: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[Keys.WHITELIST] ?: emptySet()
            preferences[Keys.WHITELIST] = if (whitelisted) {
                current + packageName
            } else {
                current - packageName
            }
        }
    }

    suspend fun setBlacklisted(packageName: String, blacklisted: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[Keys.BLACKLIST] ?: DefaultData.BLACKLIST
            preferences[Keys.BLACKLIST] = if (blacklisted) {
                current + packageName
            } else {
                current - packageName
            }
        }
    }

    /**
     * Adds a ViewId captured by the layout inspector. Returns false when the
     * input is invalid or the rule is already present.
     */
    suspend fun addCustomViewId(
        packageName: String,
        rawViewId: String,
        activityName: String = "",
        globalInAppRule: Boolean = true,
    ): Boolean {
        val pkg = packageName.trim()
        val viewId = normalizeRuleViewId(rawViewId)
        if (pkg.isEmpty() || viewId.isEmpty()) return false
        val activity = activityName.trim()

        var added = false
        dataStore.edit { preferences ->
            val current = decodeCustomRules(preferences[Keys.CUSTOM_RULES]).toMutableMap()
            val rule = current[pkg] ?: LocalSkipRule()
            if (rule.viewIds.none { it.viewId == viewId }) {
                current[pkg] = rule.copy(
                    viewIds = rule.viewIds + LocalViewIdRule(
                        viewId = viewId,
                        activityName = activity,
                        globalInAppRule = globalInAppRule,
                    ),
                )
                preferences[Keys.CUSTOM_RULES] = encodeCustomRules(current)
                added = true
            }
        }
        return added
    }

    /**
     * Adds a normalized coordinate blind-tap rule. A coordinate within 0.5% of
     * an existing point for the same package is treated as a duplicate.
     */
    suspend fun addCustomCoordinate(
        packageName: String,
        percentX: Float,
        percentY: Float,
        activityName: String = "",
        globalInAppRule: Boolean = true,
    ): Boolean {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return false
        val activity = activityName.trim()
        val point = NormalizedPoint(
            x = percentX.coerceIn(0f, 1f),
            y = percentY.coerceIn(0f, 1f),
        )

        var added = false
        dataStore.edit { preferences ->
            val current = decodeCustomRules(preferences[Keys.CUSTOM_RULES]).toMutableMap()
            val rule = current[pkg] ?: LocalSkipRule()
            val duplicate = rule.coordinates.any { existing ->
                abs(existing.point.x - point.x) < 0.005f &&
                    abs(existing.point.y - point.y) < 0.005f &&
                    existing.activityName == activity
            }
            if (!duplicate) {
                current[pkg] = rule.copy(
                    coordinates = (
                        rule.coordinates + LocalCoordinateRule(
                            point = point,
                            activityName = activity,
                            globalInAppRule = globalInAppRule,
                        )
                        ).takeLast(MAX_COORDINATES_PER_PACKAGE),
                )
                preferences[Keys.CUSTOM_RULES] = encodeCustomRules(current)
                added = true
            }
        }
        return added
    }

    /**
     * Adds a compact text rule captured from the visual inspector. Used when a
     * close button has readable text but no stable ViewId.
     */
    suspend fun addCustomText(
        packageName: String,
        rawText: String,
        activityName: String = "",
        globalInAppRule: Boolean = true,
    ): Boolean {
        val pkg = packageName.trim()
        val text = normalizeRuleText(rawText)
        if (pkg.isEmpty() || text.isEmpty()) return false
        val activity = activityName.trim()

        var added = false
        dataStore.edit { preferences ->
            val current = decodeCustomRules(preferences[Keys.CUSTOM_RULES]).toMutableMap()
            val rule = current[pkg] ?: LocalSkipRule()
            val duplicate = rule.texts.any {
                it.text.equals(text, ignoreCase = true) && it.activityName == activity
            }
            if (!duplicate) {
                current[pkg] = rule.copy(
                    texts = (
                        rule.texts + LocalTextRule(
                            text = text,
                            activityName = activity,
                            globalInAppRule = globalInAppRule,
                        )
                        ).takeLast(MAX_TEXTS_PER_PACKAGE),
                )
                preferences[Keys.CUSTOM_RULES] = encodeCustomRules(current)
                added = true
            }
        }
        return added
    }

    /**
     * One-time migration for 1.5.0. Earlier versions could persist overly broad
     * or stale custom rules, so the first run of the safety release clears them
     * exactly once.
     */
    suspend fun clearLegacyRulesOnce() {
        dataStore.edit { preferences ->
            if (preferences[Keys.CLEAR_LEGACY_RULES_ONCE] == true) return@edit
            preferences.remove(Keys.CUSTOM_RULES)
            preferences[Keys.CLEAR_LEGACY_RULES_ONCE] = true
        }
    }

    suspend fun clearCustomRules() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.CUSTOM_RULES)
        }
    }

    suspend fun recordSkip(log: InterceptLog) {
        recordEvent(log, incrementSkip = true, incrementBlock = false)
    }

    suspend fun recordJumpBlock(log: InterceptLog) {
        recordEvent(log, incrementSkip = false, incrementBlock = true)
    }

    suspend fun recordPopupBlock(log: InterceptLog) {
        recordEvent(log, incrementSkip = false, incrementBlock = false)
    }

    private suspend fun recordEvent(
        log: InterceptLog,
        incrementSkip: Boolean,
        incrementBlock: Boolean,
    ) {
        dataStore.edit { preferences ->
            val today = DefaultData.todayKey()
            if (preferences[Keys.STATS_DATE] != today) {
                preferences[Keys.STATS_DATE] = today
                preferences[Keys.SKIP_COUNT] = 0
                preferences[Keys.BLOCK_COUNT] = 0
            }
            if (incrementSkip) {
                preferences[Keys.SKIP_COUNT] = (preferences[Keys.SKIP_COUNT] ?: 0) + 1
            }
            if (incrementBlock) {
                preferences[Keys.BLOCK_COUNT] = (preferences[Keys.BLOCK_COUNT] ?: 0) + 1
            }

            val updated = buildList {
                add(log)
                addAll(decodeLogs(preferences[Keys.LOGS]))
            }.take(MAX_LOG_ENTRIES)
            preferences[Keys.LOGS] = encodeLogs(updated)
        }
    }

    suspend fun clearLogs() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.LOGS)
        }
    }

    /**
     * Resets yesterday's counters when the UI is resumed or the service starts.
     * The flow mapping already reports zero for a stale date; this also writes
     * the reset so the persisted state stays consistent.
     */
    suspend fun ensureFreshCounters() {
        val today = DefaultData.todayKey()
        val snapshot = dataStore.data
            .catch { throwable ->
                if (throwable is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw throwable
                }
            }
            .first()
        if (snapshot[Keys.STATS_DATE] == today) return
        dataStore.edit { preferences ->
            if (preferences[Keys.STATS_DATE] != today) {
                preferences[Keys.STATS_DATE] = today
                preferences[Keys.SKIP_COUNT] = 0
                preferences[Keys.BLOCK_COUNT] = 0
            }
        }
    }

    /**
     * Some permission UI surfaces call this to get the freshest counter without
     * waiting for a collector.
     */
    suspend fun currentSettings(): AppSettings {
        return dataStore.data
            .catch { throwable ->
                if (throwable is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw throwable
                }
            }
            .first()
            .toAppSettings()
    }

    private fun Preferences.toAppSettings(): AppSettings {
        val today = DefaultData.todayKey()
        val savedDate = this[Keys.STATS_DATE] ?: today
        val countsAreFresh = savedDate == today
        val localRules = decodeCustomRules(this[Keys.CUSTOM_RULES])
        return AppSettings(
            whitelist = (this[Keys.WHITELIST] ?: emptySet()).toSet(),
            blacklist = (this[Keys.BLACKLIST] ?: DefaultData.BLACKLIST).toSet(),
            skipCount = if (countsAreFresh) this[Keys.SKIP_COUNT] ?: 0 else 0,
            blockCount = if (countsAreFresh) this[Keys.BLOCK_COUNT] ?: 0 else 0,
            customRuleCount = localRules.values.sumOf { it.size },
            statsDate = if (countsAreFresh) today else savedDate,
            isUserAgreementAccepted = this[Keys.USER_AGREEMENT_ACCEPTED] ?: false,
        )
    }

    private fun decodeLogs(raw: String?): List<InterceptLog> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        InterceptLog(
                            id = item.optString("id").ifBlank {
                                UUID.randomUUID().toString()
                            },
                            timestamp = item.optLong("timestamp", 0L),
                            type = InterceptLogType.fromStorage(item.optString("type")),
                            packageName = item.optString("packageName"),
                            sourcePackage = if (item.isNull("sourcePackage")) {
                                null
                            } else {
                                item.optString("sourcePackage").ifBlank { null }
                            },
                            trigger = item.optString("trigger"),
                            detail = item.optString("detail"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeLogs(logs: List<InterceptLog>): String {
        val array = JSONArray()
        logs.forEach { log ->
            array.put(
                JSONObject().apply {
                    put("id", log.id)
                    put("timestamp", log.timestamp)
                    put("type", log.type.storageKey)
                    put("packageName", log.packageName)
                    put("sourcePackage", log.sourcePackage ?: JSONObject.NULL)
                    put("trigger", log.trigger)
                    put("detail", log.detail)
                },
            )
        }
        return array.toString()
    }

    private fun decodeCustomRules(raw: String?): Map<String, LocalSkipRule> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            val result = linkedMapOf<String, LocalSkipRule>()
            val packages = root.keys()
            while (packages.hasNext()) {
                val rawKey = packages.next()
                val packageName = rawKey.trim()
                if (packageName.isEmpty()) continue

                val value = root.opt(rawKey)
                val viewIds = mutableListOf<LocalViewIdRule>()
                val coordinates = mutableListOf<LocalCoordinateRule>()
                val texts = mutableListOf<LocalTextRule>()
                when (value) {
                    // Backward compatibility with the 1.1.0 array-only format.
                    is JSONArray -> {
                        for (index in 0 until value.length()) {
                            val normalized = normalizeRuleViewId(value.optString(index))
                            if (normalized.isNotEmpty()) {
                                viewIds += LocalViewIdRule(viewId = normalized)
                            }
                        }
                    }

                    is JSONObject -> {
                        val idArray = value.optJSONArray("viewIds")
                        if (idArray != null) {
                            for (index in 0 until idArray.length()) {
                                val parsed = parseViewIdRule(idArray.opt(index)) ?: continue
                                if (viewIds.none { it.viewId == parsed.viewId }) {
                                    viewIds += parsed
                                }
                            }
                        }
                        val pointArray = value.optJSONArray("coordinates")
                            ?: value.optJSONArray("points")
                        if (pointArray != null) {
                            for (index in 0 until pointArray.length()) {
                                val parsed = parseCoordinateRule(pointArray.opt(index)) ?: continue
                                if (coordinates.none {
                                        abs(it.point.x - parsed.point.x) < 0.005f &&
                                            abs(it.point.y - parsed.point.y) < 0.005f &&
                                            it.activityName == parsed.activityName
                                    }
                                ) {
                                    coordinates += parsed
                                }
                            }
                        }
                        val textArray = value.optJSONArray("texts")
                        if (textArray != null) {
                            for (index in 0 until textArray.length()) {
                                val parsed = parseTextRule(textArray.opt(index)) ?: continue
                                if (texts.none {
                                        it.text.equals(parsed.text, ignoreCase = true) &&
                                            it.activityName == parsed.activityName
                                    }
                                ) {
                                    texts += parsed
                                }
                            }
                        }
                    }
                }

                if (viewIds.isNotEmpty() || coordinates.isNotEmpty() || texts.isNotEmpty()) {
                    result[packageName] = LocalSkipRule(
                        viewIds = viewIds.distinctBy { it.viewId },
                        coordinates = coordinates.takeLast(MAX_COORDINATES_PER_PACKAGE),
                        texts = texts.takeLast(MAX_TEXTS_PER_PACKAGE),
                    )
                }
            }
            result
        }.getOrDefault(emptyMap())
    }

    private fun parseViewIdRule(value: Any?): LocalViewIdRule? {
        return when (value) {
            is String -> normalizeRuleViewId(value).takeIf { it.isNotEmpty() }
                ?.let { LocalViewIdRule(viewId = it) }

            is JSONObject -> {
                val normalized = normalizeRuleViewId(value.optString("viewId"))
                if (normalized.isEmpty()) {
                    null
                } else {
                    LocalViewIdRule(
                        viewId = normalized,
                        activityName = value.optString("activityName"),
                        globalInAppRule = if (value.has("globalInAppRule")) {
                            value.optBoolean("globalInAppRule", true)
                        } else {
                            true
                        },
                        createdAt = value.optLong("createdAt", System.currentTimeMillis()),
                    )
                }
            }

            else -> null
        }
    }

    private fun parseTextRule(value: Any?): LocalTextRule? {
        val item = value as? JSONObject ?: return null
        val text = normalizeRuleText(item.optString("text"))
        if (text.isEmpty()) return null
        return LocalTextRule(
            text = text,
            activityName = item.optString("activityName"),
            globalInAppRule = if (item.has("globalInAppRule")) {
                item.optBoolean("globalInAppRule", true)
            } else {
                true
            },
            createdAt = item.optLong("createdAt", System.currentTimeMillis()),
        )
    }

    private fun parseCoordinateRule(value: Any?): LocalCoordinateRule? {
        val item = value as? JSONObject ?: return null
        val x = item.optDouble("x", Double.NaN)
        val y = item.optDouble("y", Double.NaN)
        if (x.isNaN() || y.isNaN()) return null
        return LocalCoordinateRule(
            point = NormalizedPoint(
                x = x.coerceIn(0.0, 1.0).toFloat(),
                y = y.coerceIn(0.0, 1.0).toFloat(),
            ),
            activityName = item.optString("activityName"),
            globalInAppRule = if (item.has("globalInAppRule")) {
                item.optBoolean("globalInAppRule", true)
            } else {
                true
            },
            createdAt = item.optLong("createdAt", System.currentTimeMillis()),
        )
    }

    private fun encodeCustomRules(rules: Map<String, LocalSkipRule>): String {
        val root = JSONObject()
        rules.toSortedMap().forEach { (packageName, rule) ->
            root.put(
                packageName,
                JSONObject().apply {
                    put(
                        "viewIds",
                        JSONArray().apply {
                            rule.viewIds.sortedBy { it.viewId }.forEach { item ->
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
                },
            )
        }
        return root.toString()
    }

    private fun normalizeRuleViewId(raw: String): String =
        raw.trim().substringAfterLast(':').substringAfterLast('/').lowercase()

    private fun normalizeRuleText(raw: String): String =
        raw.replace(Regex("[\\s\u200B-\u200D\uFEFF]"), "").take(32)

    private object Keys {
        val WHITELIST = stringSetPreferencesKey("whitelist")
        val BLACKLIST = stringSetPreferencesKey("blacklist")
        val SKIP_COUNT = intPreferencesKey("skip_count")
        val BLOCK_COUNT = intPreferencesKey("block_count")
        val STATS_DATE = stringPreferencesKey("stats_date")
        val LOGS = stringPreferencesKey("intercept_logs")
        val CUSTOM_RULES = stringPreferencesKey("custom_skip_rules")
        val CLEAR_LEGACY_RULES_ONCE = booleanPreferencesKey("clear_legacy_rules_once_v150")
        val USER_AGREEMENT_ACCEPTED = booleanPreferencesKey("user_agreement_accepted")
    }

    private companion object {
        const val MAX_LOG_ENTRIES = 100
        const val MAX_TEXTS_PER_PACKAGE = 16
        const val MAX_COORDINATES_PER_PACKAGE = 20
    }
}
