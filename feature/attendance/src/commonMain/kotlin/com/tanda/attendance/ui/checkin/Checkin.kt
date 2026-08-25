package com.tanda.attendance.ui.checkin

import com.tanda.biometrics.domain.usecase.IdentificationUsecase
import com.tanda.biometrics.ui.fingerprint.Fingerprint
import com.tanda.core.common.concurrent.Dispatcher
import com.tanda.core.ui.component.UiComponent
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.core.ui.factory.UiBuilderFactory
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module

object Checkin {
    class Builder(scope: Scope): UiComponent.ComponentBuilder(scope) {
        override fun build(): Scope {
            val scope = scope(named<Checkin>())
            scope.getKoin().loadModules(listOf(
                module {
                    scope<Checkin> {
                        scoped {
                            CheckinViewModel(
                                dispatcher = get<Dispatcher>(),
                                usecase = get<IdentificationUsecase>(),
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
