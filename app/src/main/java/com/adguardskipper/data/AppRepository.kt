package com.adguardskipper.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import com.adguardskipper.data.model.InstalledApp
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** PackageManager access kept off the main thread. */
class AppRepository(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager

    suspend fun loadLaunchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val launcherPackages = linkedSetOf<String>()
        val applications = linkedMapOf<String, android.content.pm.ApplicationInfo>()

        // Channel 1: direct launcher-activity query.
        runCatching { queryActivities(launcherIntent) }.getOrDefault(emptyList())
            .forEach { resolveInfo ->
                val info = resolveInfo.activityInfo?.applicationInfo ?: return@forEach
                if (info.packageName == appContext.packageName) return@forEach
                launcherPackages += info.packageName
                applications[info.packageName] = info
            }

        // Channel 2: installed packages. Some OEM ROMs return an incomplete
        // launcher query, so merge packages that expose a launch intent. System
        // and updated-system apps are preserved; the UI decides what to show.
        runCatching { installedPackages() }.getOrDefault(emptyList())
            .forEach { packageInfo ->
                val info = packageInfo.applicationInfo ?: return@forEach
                val packageName = info.packageName
                if (packageName == appContext.packageName) return@forEach
                val hasLauncher = launcherPackages.contains(packageName) ||
                    runCatching { packageManager.getLaunchIntentForPackage(packageName) }
                        .getOrNull() != null
                if (!hasLauncher) return@forEach
                launcherPackages += packageName
                if (!applications.containsKey(packageName)) {
                    applications[packageName] = info
                }
            }

        val collator = Collator.getInstance(Locale.getDefault()).apply {
            strength = Collator.PRIMARY
        }
        applications.values
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = info.loadLabel(packageManager).toString().ifBlank { info.packageName },
                    // Preinstalled-but-updated apps (Meituan/Douyin on many ROMs)
                    // must remain in the default third-party list.
                    isSystemApp = info.isRealSystemApp(),
                )
            }
            .sortedWith { first, second -> collator.compare(first.label, second.label) }
    }

    /**
     * Resolves all HOME activities dynamically. This covers alternate launchers
     * from third-party launchers and OEM-specific desk packages.
     */
    suspend fun loadLauncherPackages(): Set<String> = withContext(Dispatchers.IO) {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val candidates = linkedSetOf<String>()

        queryActivities(homeIntent)
            .mapNotNull { it.activityInfo?.packageName }
            .filter { it != "android" && it != appContext.packageName }
            .forEach(candidates::add)

        val defaultHome = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.resolveActivity(
                homeIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        defaultHome?.activityInfo?.packageName
            ?.takeIf { it != "android" && it != appContext.packageName }
            ?.let(candidates::add)

        candidates.filter { pkg ->
            runCatching { packageManager.getApplicationInfo(pkg, 0) }.isSuccess
        }.toSet()
    }

    fun labelForPackage(packageName: String): String {
        if (packageName.isBlank()) return packageName
        return runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            info.loadLabel(packageManager).toString().ifBlank { packageName }
        }.getOrDefault(packageName)
    }

    private fun queryActivities(intent: Intent): List<ResolveInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
    }

    private fun installedPackages(): List<android.content.pm.PackageInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(0)
        }
    }

    private fun android.content.pm.ApplicationInfo.isRealSystemApp(): Boolean {
        val system = (flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        val updatedSystem =
            (flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        return system && !updatedSystem
    }
}
