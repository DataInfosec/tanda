package com.tanda.biometrics.ui.scanner

import com.tanda.biometrics.domain.usecase.CaptureUsecase
import com.tanda.biometrics.domain.usecase.ObserveModeUsecase
import com.tanda.biometrics.domain.usecase.ObserveStateUsecase
import com.tanda.biometrics.domain.usecase.PermissionRequestUsecase
import com.tanda.biometrics.domain.usecase.PermissionUsecase
import com.tanda.core.ui.component.UiComponent
import org.koin.core.annotation.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module
import org.koin.ksp.generated.*

@Module
object Scanner {
    @org.koin.core.annotation.Scope(Scanner::class)
    fun provideViewModel(
        stateUsecase: ObserveStateUsecase,
        modeUsecase: ObserveModeUsecase,
        permissionUsecase: PermissionUsecase,
        permissionRequestUsecase: PermissionRequestUsecase,
        captureUsecase: CaptureUsecase
    ): ScannerViewModel {
        return ScannerViewModel(
            stateUsecase = stateUsecase,
            modeUsecase = modeUsecase,
            permissionUsecase = permissionUsecase,
            permissionRequestUsecase = permissionRequestUsecase,
            captureUsecase = captureUsecase
        )
    }

    class Builder(scope: Scope): UiComponent.ComponentBuilder(scope) {
        override fun build(): Scope {
            val scope = scope(named<Scanner>())
            scope.getKoin().loadModules(listOf(
                    module { scope<Scanner> { } },
                Scanner.module
            ))
            return scope
        }
    }
}
