package com.tanda

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.SharedPreferencesSettings
import com.tanda.ui.main.MainScreen
import org.koin.dsl.module

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
            MainScreen(scope.id)
        }
    }
}
