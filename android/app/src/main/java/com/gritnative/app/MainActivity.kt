package com.gritnative.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gritnative.app.data.StoreRepository
import com.gritnative.app.data.AppLocale
import com.gritnative.app.data.ThemeMode
import com.gritnative.app.timer.AodOverlayWindow
import com.gritnative.app.ui.AppNav
import com.gritnative.app.ui.Aod
import com.gritnative.app.ui.components.AppLanguage
import com.gritnative.app.ui.components.LocalArabic
import com.gritnative.app.ui.theme.GritTheme
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val requestedTab = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedTab.value = intent?.getStringExtra(EXTRA_TAB)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        // Ask after the first frame: launching a permission request directly
        // from onCreate can race the activity becoming visible on some OEMs.
        window.decorView.postDelayed({ requestNotificationPermissionOnce() }, 600L)

        setContent {
            val settings by StoreRepository.settings.collectAsState()
            val store by StoreRepository.store.collectAsState()
            val pendingTab by requestedTab.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val arabic = when (settings.language) {
                "ar" -> true
                "en" -> false
                else -> Locale.getDefault().language.startsWith("ar")
            }
            // Read in the composition itself (not only inside the SideEffect)
            // so a change always recomposes this scope and re-runs the flag
            // pass below.
            val aodActive = Aod.active
            val timerRunning = store.timer?.running == true
            SideEffect {
                AppLanguage.isArabic = arabic
                AppLocale.isArabic = arabic
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
                // While the always-on countdown is up, the screen must not
                // sleep, the dim surface has to stay visible even when the
                // phone locks itself under it, and the system bars step aside
                // so the display is truly full screen.
                val keepOn = aodActive && timerRunning
                if (keepOn) {
                    window.addFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    )
                    window.attributes = window.attributes.apply {
                        screenBrightness = AOD_BRIGHTNESS
                    }
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        hide(WindowInsetsCompat.Type.systemBars())
                    }
                } else {
                    window.clearFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    )
                    window.attributes = window.attributes.apply {
                        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
                    WindowCompat.getInsetsController(window, window.decorView)
                        .show(WindowInsetsCompat.Type.systemBars())
                }
            }
            CompositionLocalProvider(
                LocalArabic provides arabic,
                LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                GritTheme(settings) {
                    AppNav(
                        requestedTab = pendingTab,
                        onRequestHandled = { requestedTab.value = null }
                    )
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        // Any touch is a wake-up: leave the always-on display and restart its
        // inactivity clock.
        if (Aod.active) Aod.exit() else Aod.noteInteraction()
    }

    override fun onPause() {
        super.onPause()
        // The in-app dim layer is about to be covered: hand the countdown to
        // the system overlay so it survives other apps and the lock screen.
        if (Aod.active && StoreRepository.currentStore.timer?.running == true) {
            AodOverlayWindow.show(this)
        }
    }

    override fun onResume() {
        super.onResume()
        // The app is front and center again — the in-app layer takes over.
        AodOverlayWindow.dismiss()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedTab.value = intent.getStringExtra(EXTRA_TAB)
    }

    private fun requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return
        val prefs = getSharedPreferences("grit_permissions", MODE_PRIVATE)
        if (prefs.getBoolean(KEY_NOTIFICATION_ASKED, false)) return
        prefs.edit().putBoolean(KEY_NOTIFICATION_ASKED, true).apply()
        runCatching { notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    companion object {
        const val EXTRA_TAB = "com.panadoro.app.extra.TAB"
        private const val KEY_NOTIFICATION_ASKED = "notification_asked"

        /** Dim enough to sip power, bright enough to read across the room. */
        private const val AOD_BRIGHTNESS = 0.12f
    }
}
