package com.tanda.ui.main

import com.tanda.biometrics.domain.usecase.ObserveStatusUsecase
import com.tanda.biometrics.domain.usecase.StartUsecase
import com.tanda.biometrics.domain.usecase.StopUsecase
import com.tanda.biometrics.ui.scanner.Scanner
import com.tanda.core.ui.component.UiComponent
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.core.ui.factory.UiBuilderFactory
import com.tanda.module.AppModule
import org.koin.core.annotation.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module
import org.koin.ksp.generated.*

@Module
object Main {
    @org.koin.core.annotation.Scope(Main::class)
    fun provideViewModel(
        startUsecase: StartUsecase,
        stopUsecase: StopUsecase,
        observeStatusUsecase: ObserveStatusUsecase,
    ): MainViewModel {
        return MainViewModel(
            startUsecase,
            stopUsecase,
            observeStatusUsecase
        )
    }

    class Builder(scope: Scope): UiComponent.ComponentBuilder(scope) {
        override fun build(): Scope {
            val scope = scope(named<Main>())
            scope.getKoin().loadModules(listOf(
                    module { scope<Main> {
                        scoped<UiComponentProvider.Factory> {
                            UiBuilderFactory(
                                listOf(
                                    this@Builder,
                                    Scanner.Builder(scope),
                                )
                            )
                        }
                    } },
                AppModule.module,
                Main.module
            ))
            return scope
        }
    }
}
