package com.tanda

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.SharedPreferencesSettings
import com.tanda.biometrics.device.interactor.ScannerInteractor
import com.tanda.biometrics.device.scanner.ScannerService
import com.tanda.core.ui.extension.scopeOf
import com.tanda.module.TandaModule
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module
import org.koin.ksp.generated.module

class TandaApplication : Application(), ScannerService.Provider {
    override lateinit var scope: Scope

    override fun onCreate() {
        super.onCreate()
        val context = this
        scope = startKoin {
            modules(
                module {
                    single<Application> { this@TandaApplication }
                    single<ObservableSettings> {
                        SharedPreferencesSettings(
                            getSharedPreferences(javaClass.name, MODE_PRIVATE)
                        )
                    }
                    single<ScannerInteractor> {
                        ScannerInteractor(context, get(), get())
                    }
                },
                TandaModule.module
            )
        }.koin.scopeOf(named<TandaApplication>())
        startScannerServiceIfNeeded()
    }

    private fun startScannerServiceIfNeeded() {
        val intent = Intent(this, ScannerService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}
