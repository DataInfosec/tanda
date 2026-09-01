package com.tanda.biometrics.ui.scanner

import com.tanda.biometrics.domain.usecase.ObserveStatusUsecase
import com.tanda.biometrics.domain.usecase.StartUsecase
import com.tanda.biometrics.ui.fingerprint.Fingerprint
import com.tanda.core.ui.component.UiComponent
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.core.ui.factory.UiBuilderFactory
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module

object Scanner {
    class Builder(scope: Scope): UiComponent.ComponentBuilder(scope) {
        override fun build(): Scope {
            val scope = scope(named<Scanner>())
            scope.getKoin().loadModules(listOf(
                module {
                    scope<Scanner> {
                        scoped {
                            ScannerViewModel(
                                startUsecase = get<StartUsecase>(),
                                observeStatusUsecase = get<ObserveStatusUsecase>()
                            )
                        }
                        factory<UiComponentProvider.Factory> {
                            UiBuilderFactory(
                                listOf(
                                    this@Builder,
                                    Fingerprint.Builder(scope),
                                )
                            )
                        }
                    }
                }
            ))
            return scope
        }
    }
}
