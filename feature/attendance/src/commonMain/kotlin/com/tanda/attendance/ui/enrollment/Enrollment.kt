package com.tanda.attendance.ui.enrollment

import com.tanda.biometrics.domain.usecase.EnrollmentUsecase
import com.tanda.biometrics.ui.fingerprint.Fingerprint
import com.tanda.core.common.concurrent.Dispatcher
import com.tanda.core.ui.component.UiComponent
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.core.ui.factory.UiBuilderFactory
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module

object Enrollment {
    class Builder(scope: Scope): UiComponent.ComponentBuilder(scope) {
        override fun build(): Scope {
            val scope = scope(named<Enrollment>())
            scope.getKoin().loadModules(listOf(
                module {
                    scope<Enrollment> {
                        scoped {
                            EnrollmentViewModel(
                                dispatcher = get<Dispatcher>(),
                                usecase = get<EnrollmentUsecase>(),
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
