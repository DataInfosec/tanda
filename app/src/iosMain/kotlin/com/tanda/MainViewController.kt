package com.tanda

import androidx.compose.ui.window.ComposeUIViewController
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.ObservableSettings
import com.tanda.biometrics.device.interactor.ScannerInteractor
import com.tanda.core.ui.extension.scopeOf
import com.tanda.ui.main.MainScreen
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

interface TandaApplication

object IosApp {
    private val koin = startKoin {}.koin
    val scope = koin.scopeOf(named<TandaApplication>())

    init {
        scope.getKoin().loadModules(listOf(
            module {
                single<ScannerInteractor> { ScannerInteractor() }
                single<ObservableSettings> {
                    NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults())
                }
            }
        ))
    }
}

fun MainViewController() = ComposeUIViewController {
    MainScreen(IosApp.scope.id)
}
