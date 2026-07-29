package com.tanda

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.SharedPreferencesSettings
import com.tanda.ui.main.Main
import com.tanda.ui.main.MainScreen
import org.koin.dsl.module

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val scope = (application as TandaApplication).scope
        scope.getKoin().loadModules(listOf(
            module {
                single<ObservableSettings> {
                    SharedPreferencesSettings(
                        getPreferences(MODE_PRIVATE)
                    )
                }
            }
        ))
        setContent {
            val view = LocalView.current
            val isDarkTheme = isSystemInDarkTheme()
            val component = remember { Main.Builder(scope).build() }
            SideEffect {
                if (view.context !is Activity) return@SideEffect
                val window = (view.context as Activity).window
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                val windowsInsetsController = WindowCompat.getInsetsController(window, view)
                windowsInsetsController.isAppearanceLightStatusBars = !isDarkTheme
                windowsInsetsController.isAppearanceLightNavigationBars = !isDarkTheme
            }
            MainScreen(component.id)
        }
    }
}
