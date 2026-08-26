package com.tanda.ui.main

import com.tanda.account.ui.login.Login
import com.tanda.biometrics.domain.usecase.StartUsecase
import com.tanda.biometrics.domain.usecase.StopUsecase
import com.tanda.core.common.concurrent.Dispatcher
import com.tanda.core.common.interactor.LocaleInteractor
import com.tanda.core.ui.component.UiComponent
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.core.ui.factory.UiBuilderFactory
import com.tanda.ui.home.Home
import com.tanda.ui.splash.Splash
import org.koin.core.annotation.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module
import org.koin.ksp.generated.module

@Module
object Main {
    @org.koin.core.annotation.Scope(Main::class)
    fun provideViewModel(
        dispatcher: Dispatcher,
        usecase: StartUsecase,
        stopUsecase: StopUsecase,
        interactor: LocaleInteractor,
    ): MainViewModel {
        return MainViewModel(
            dispatcher = dispatcher,
            usecase = usecase,
            stopUsecase = stopUsecase,
            interactor = interactor
        )
    }

    class Builder(scope: Scope): UiComponent.ComponentBuilder(scope) {
        override fun build(): Scope {
            val scope = scope(named<Main>())
            scope.getKoin().loadModules(listOf(
                module {
                    scope<Main> {
                        factory<UiComponentProvider.Factory> {
                            UiBuilderFactory(
                                listOf(
                                    this@Builder,
                                    Splash.Builder(scope),
                                    Home.Builder(scope),
                                    Login.Builder(scope),
                                )
                            )
                        }
                    }
                },
                Main.module
            ))
            return scope
        }
    }
}
