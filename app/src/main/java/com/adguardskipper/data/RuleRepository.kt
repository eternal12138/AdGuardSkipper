package com.adguardskipper.data

import android.content.Context
import android.util.Log
import com.adguardskipper.data.model.NormalizedPoint
import com.adguardskipper.data.model.SkipRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class RuleBundle(
    val version: Int = 0,
    val rules: Map<String, SkipRule> = emptyMap(),
)

/**
 * Loads package-specific rules from assets. Format support is intentionally
 * tolerant: "rules" may be a JSON array, or "packages" may be an object whose
 * keys are package names.
 */
class RuleRepository(context: Context) {
    private val appContext = context.applicationContext

    suspend fun load(): RuleBundle = withContext(Dispatchers.IO) {
        runCatching {
            val raw = appContext.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            parse(raw)
        }.onFailure { error ->
            Log.w(TAG, "Unable to load $ASSET_NAME", error)
        }.getOrDefault(RuleBundle())
    }

    private fun parse(raw: String): RuleBundle {
        val root = JSONObject(raw)
        val version = root.optInt("version", 1)
        val parsed = linkedMapOf<String, SkipRule>()

        root.optJSONArray("rules")?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val packageName = item.optString("packageName").trim()
                if (packageName.isBlank()) continue
                parsed[packageName] = parseRule(packageName, item)
            }
        }

        root.optJSONObject("packages")?.let { packages ->
            val keys = packages.keys()
            while (keys.hasNext()) {
                val packageName = keys.next().trim()
                if (packageName.isBlank()) continue
                val item = packages.optJSONObject(packageName) ?: continue
                parsed[packageName] = parseRule(packageName, item)
            }
        }

        return RuleBundle(version = version, rules = parsed)
    }

    private fun parseRule(packageName: String, item: JSONObject): SkipRule {
        return SkipRule(
            packageName = packageName,
            viewIds = item.optJSONArray("viewIds").toStringList(),
            texts = item.optJSONArray("texts").toStringList(),
            points = item.optJSONArray("points").toPointList(),
            note = item.optString("note"),
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).trim().takeIf { it.isNotEmpty() }?.let(::add)
            }
        }.distinct()
    }

    private fun JSONArray?.toPointList(): List<NormalizedPoint> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val x = item.optDouble("x", Double.NaN)
                val y = item.optDouble("y", Double.NaN)
                if (x.isNaN() || y.isNaN()) continue
                add(
                    NormalizedPoint(
                        x = x.coerceIn(0.0, 1.0).toFloat(),
                        y = y.coerceIn(0.0, 1.0).toFloat(),
                    ),
                )
            }
        }
    }

    private companion object {
        const val ASSET_NAME = "default_rules.json"
        const val TAG = "RuleRepository"
    }
}
