package com.adguardskipper

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adguardskipper.ui.AdGuardSkipperRoot
import com.adguardskipper.ui.CialloSplashScreen
import com.adguardskipper.ui.MainViewModel
import com.adguardskipper.ui.components.UserAgreementDialog
import com.adguardskipper.ui.theme.AdGuardSkipperTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        // The Ciallo theme is always light even when the system is in dark mode.
        // Re-assert dark status/navigation icons so they stay legible over the
        // powder-blue backdrop on OEM ROMs that ignore the activity theme flag.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent {
            val viewModel: MainViewModel = viewModel(
                factory = MainViewModel.Factory(application),
            )
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.refreshPermissions()
                        viewModel.refreshApps(force = true)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            var showSplash by rememberSaveable { mutableStateOf(true) }
            val agreementAccepted by viewModel.agreementAccepted.collectAsStateWithLifecycle()
            val agreementLoaded by viewModel.agreementLoaded.collectAsStateWithLifecycle()

            LaunchedEffect(agreementLoaded) {
                if (!agreementLoaded) return@LaunchedEffect
                delay(1500)
                showSplash = false
            }

            AdGuardSkipperTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Crossfade(
                        targetState = showSplash,
                        animationSpec = tween(durationMillis = 360),
                        label = "cialloSplash",
                    ) { splashVisible ->
                        if (splashVisible) {
                            CialloSplashScreen()
                        } else {
                            AdGuardSkipperRoot(viewModel = viewModel)
                        }
                    }

                    if (!showSplash && agreementLoaded && !agreementAccepted) {
                        UserAgreementDialog(
                            isFirstRun = true,
                            onAccept = { viewModel.setUserAgreementAccepted(true) },
                            onDecline = { finishAffinity() },
                            onDismiss = {},
                        )
                    }
                }
            }
        }
    }
}
