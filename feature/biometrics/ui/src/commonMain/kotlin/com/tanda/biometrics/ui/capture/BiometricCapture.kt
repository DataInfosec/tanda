package com.tanda.biometrics.ui.capture

import com.tanda.biometrics.domain.usecase.ReadSubjectUsecase
import com.tanda.biometrics.domain.usecase.EnrollmentUsecase
import com.tanda.biometrics.domain.usecase.IdentificationUsecase
import com.tanda.biometrics.ui.fingerprint.Fingerprint
import com.tanda.core.common.concurrent.Dispatcher
import com.tanda.core.ui.component.UiComponent
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.core.ui.factory.UiBuilderFactory
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module

object BiometricCapture {
    class Builder(scope: Scope): UiComponent.ComponentBuilder(scope) {
        override fun build(): Scope {
            val scope = scope(named<BiometricCapture>())
            scope.getKoin().loadModules(listOf(
                module { scope<BiometricCapture> {
                    scoped {
                        SubjectBiometricViewModel(
                            dispatcher = get<Dispatcher>(),
                            readSubjectUsecase = get<ReadSubjectUsecase>(),
                            identificationUsecase = get<IdentificationUsecase>(),
                            enrollmentUsecase = get<EnrollmentUsecase>()
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
