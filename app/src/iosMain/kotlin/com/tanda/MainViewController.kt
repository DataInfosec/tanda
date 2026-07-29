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
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask

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
                single<String>(qualifier = named("uuid")) {
                    get<ObservableSettings>()
                        .getStringOrNull("device_uuid")
                        ?: NSUUID().UUIDString.also {
                            get<ObservableSettings>()
                                .putString("device_uuid", it)
                        }
                }
                single<String>(qualifier = named("path")) {
                    val cachesURL = NSFileManager.defaultManager
                        .URLsForDirectory(
                            directory = NSCachesDirectory,
                            inDomains = NSUserDomainMask
                        )
                        .firstOrNull() as? NSURL
                    cachesURL?.path ?: error("Unable to resolve caches directory")
                }
            }
        ))
    }
}

fun MainViewController() = ComposeUIViewController {
    MainScreen(IosApp.scope.id)
}
