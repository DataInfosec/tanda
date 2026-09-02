package com.tanda.ui.setup

import com.tanda.account.domain.usecase.device.ActivateDeviceUsecase
import com.tanda.core.common.concurrent.Dispatcher
import com.tanda.core.ui.component.UiComponent
import org.koin.core.annotation.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module
import org.koin.ksp.generated.module

@Module
object Setup {
    @org.koin.core.annotation.Scope(Setup::class)
    fun provideViewModel(
        dispatcher: Dispatcher,
        usecase: ActivateDeviceUsecase
    ): SetupViewModel {
        return SetupViewModel(
            dispatcher = dispatcher,
            usecase = usecase
        )
    }

    class Builder(scope: Scope): UiComponent.ComponentBuilder(scope) {
        override fun build(): Scope {
            val scope = scope(named<Setup>())
            scope.getKoin().loadModules(listOf(
                module {
                    scope<Setup> {}
                },
                Setup.module
            ))
            return scope
        }
    }
}
