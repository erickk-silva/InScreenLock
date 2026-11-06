package com.inscreenlock.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.inscreenlock.app.data.SettingsRepository
import com.inscreenlock.app.ui.InScreenLockApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_InScreenLock)
        if (android.os.Build.VERSION.SDK_INT < 31) {
            window.setBackgroundDrawableResource(R.color.launch_background)
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContent {
            InScreenLockApp(
                settingsRepository = SettingsRepository(applicationContext),
                openSettingsInitially = intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false),
            )
        }
    }

    companion object {
        const val EXTRA_OPEN_SETTINGS = "com.inscreenlock.app.OPEN_SETTINGS"
    }
}
