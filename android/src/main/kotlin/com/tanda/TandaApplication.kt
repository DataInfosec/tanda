package com.tanda

import android.app.Application
import com.tanda.biometrics.device.interactor.ScannerInteractor
import com.tanda.core.ui.extension.scopeOf
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module

class TandaApplication : Application() {
    lateinit var scope: Scope

    override fun onCreate() {
        super.onCreate()
        val context = this
        scope = startKoin {
            modules(
                module {
                    single<Application> { this@TandaApplication }
                    single<ScannerInteractor> { ScannerInteractor(context) }
                }
            )
        }.koin.scopeOf(named<TandaApplication>())
    }
}
