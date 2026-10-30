package com.adguardskipper

import android.app.Application
import android.content.Context
import com.adguardskipper.data.AppPreferences
import com.adguardskipper.data.AppRepository
import com.adguardskipper.data.RuleRepository
import com.adguardskipper.service.KeepAliveHelper
import com.adguardskipper.service.NotificationHelper
import com.adguardskipper.ui.LayoutInspectorFloatWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AdGuardSkipperApp : Application() {
    lateinit var container: AppContainer
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.ensureChannel(this)
        KeepAliveHelper.ensureChannel(this)
        appScope.launch {
            container.preferences.clearLegacyRulesOnce()
        }
    }
}

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val preferences: AppPreferences = AppPreferences(appContext)
    val appRepository: AppRepository = AppRepository(appContext)
    val ruleRepository: RuleRepository = RuleRepository(appContext)
    val layoutInspector: LayoutInspectorFloatWindow =
        LayoutInspectorFloatWindow(appContext, preferences)
}
