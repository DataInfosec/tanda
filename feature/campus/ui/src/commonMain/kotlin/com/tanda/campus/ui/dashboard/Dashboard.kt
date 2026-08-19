package com.tanda.campus.ui.dashboard

import com.tanda.biometrics.ui.capture.BiometricCapture
import com.tanda.biometrics.domain.session.ScannerSessionManager
import com.tanda.campus.domain.usecase.ObserveProfileNameUsecase
import com.tanda.campus.domain.usecase.ProfileNameUsecase
import com.tanda.core.common.concurrent.Dispatcher
import com.tanda.core.ui.component.UiComponent
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.core.ui.factory.UiBuilderFactory
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module

object Dashboard {
    class Builder(scope: Scope): UiComponent.ComponentBuilder(scope) {
        override fun build(): Scope {
            val scope = scope(named<Dashboard>())
            scope.getKoin().loadModules(listOf(
                module { scope<Dashboard> {
                    scoped {
                        DashboardViewModel(
                            dispatcher = get<Dispatcher>(),
                            profileNameUsecase = get<ProfileNameUsecase>(),
                            observeProfileNameUsecase = get<ObserveProfileNameUsecase>(),
                            scannerSessionManager = get<ScannerSessionManager>(),
                        )
                    }
                    factory<UiComponentProvider.Factory> {
                        UiBuilderFactory(
                            listOf(
                                this@Builder,
                                BiometricCapture.Builder(scope),
                            )
                        )
                    }
                    }
                },
            ))
            return scope
        }
    }
}
