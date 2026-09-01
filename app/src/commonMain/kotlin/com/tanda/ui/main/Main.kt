package com.tanda.ui.main

import com.tanda.account.domain.usecase.ObserveTokenUsecase
import com.tanda.biometrics.domain.repository.DeviceConfigurationRepository
import com.tanda.account.ui.login.Login
import com.tanda.campus.ui.dashboard.Dashboard
import com.tanda.core.common.concurrent.Dispatcher
import com.tanda.core.ui.component.UiComponent
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.core.ui.factory.UiBuilderFactory
import com.tanda.module.TandaModule
import com.tanda.ui.splash.Splash
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module
import org.koin.ksp.generated.module

object Main {
    class Builder(scope: Scope): UiComponent.ComponentBuilder(scope) {
        override fun build(): Scope {
            val scope = scope(named<Main>())
            scope.getKoin().loadModules(listOf(
                module {
                    scope<Main> {
                        scoped {
                            MainViewModel(
                                dispatcher = get<Dispatcher>(),
                                observeTokenUsecase = get<ObserveTokenUsecase>(),
                                deviceConfigurationRepository = get<DeviceConfigurationRepository>(),
                            )
                        }
                        factory<UiComponentProvider.Factory> {
                            UiBuilderFactory(
                                listOf(
                                    this@Builder,
                                    Splash.Builder(scope),
                                    Dashboard.Builder(scope),
                                    Login.Builder(scope),
                                )
                            )
                        }
                    }
                },
                TandaModule.module,
            ))
            return scope
        }
    }
}
