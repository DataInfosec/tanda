package com.tanda.account.ui.login

import com.tanda.account.domain.usecase.LoginUsecase
import com.tanda.core.common.concurrent.Dispatcher
import com.tanda.core.ui.component.UiComponent
import org.koin.core.annotation.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module
import org.koin.ksp.generated.module

@Module
object Login {
    @org.koin.core.annotation.Scope(Login::class)
    fun provideViewModel(
        dispatcher: Dispatcher,
        usecase: LoginUsecase
    ): LoginViewModel {
        return LoginViewModel(
            dispatcher = dispatcher,
            usecase = usecase,
        )
    }

    class Builder(scope: Scope): UiComponent.ComponentBuilder(scope) {
        override fun build(): Scope {
            val scope = scope(named<Login>())
            scope.getKoin().loadModules(listOf(
                module {
                    scope<Login> {}
                },
                Login.module
            ))
            return scope
        }
    }
}
