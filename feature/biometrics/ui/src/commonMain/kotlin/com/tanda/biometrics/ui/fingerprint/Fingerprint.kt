package com.tanda.biometrics.ui.fingerprint

import com.tanda.biometrics.domain.usecase.CaptureUsecase
import com.tanda.biometrics.domain.usecase.ObserveModeUsecase
import com.tanda.biometrics.domain.usecase.ObserveStateUsecase
import com.tanda.biometrics.domain.usecase.PermissionRequestUsecase
import com.tanda.biometrics.domain.usecase.PermissionUsecase
import com.tanda.core.common.concurrent.Dispatcher
import com.tanda.core.ui.component.UiComponent
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module

object Fingerprint {
    class Builder(scope: Scope): UiComponent.ComponentBuilder(scope) {
        override fun build(): Scope {
            val scope = scope(named<Fingerprint>())
            scope.getKoin().loadModules(listOf(
                module {
                    scope<Fingerprint> {
                        scoped {
                            FingerprintViewModel(
                                dispatcher = get<Dispatcher>(),
                                stateUsecase = get<ObserveStateUsecase>(),
                                modeUsecase = get<ObserveModeUsecase>(),
                                permissionUsecase = get<PermissionUsecase>(),
                                permissionRequestUsecase = get<PermissionRequestUsecase>(),
                                captureUsecase = get<CaptureUsecase>()
                            )
                        }
                    }
                }
            ))
            return scope
        }
    }
}
