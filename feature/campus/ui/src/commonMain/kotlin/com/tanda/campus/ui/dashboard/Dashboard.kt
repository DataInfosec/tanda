package com.tanda.campus.ui.dashboard

import com.tanda.biometrics.ui.capture.BiometricCapture
import com.tanda.campus.domain.usecase.ObserveProfileNameUsecase
import com.tanda.campus.domain.usecase.ProfileNameUsecase
import com.tanda.core.common.concurrent.Dispatcher
import com.tanda.core.ui.component.UiComponent
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.core.ui.factory.UiBuilderFactory
import org.koin.core.annotation.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module
import org.koin.ksp.generated.module


@Module
object Dashboard {
    @org.koin.core.annotation.Scope(Dashboard::class)
    fun provideViewModel(
        dispatcher: Dispatcher,
        profileNameUsecase: ProfileNameUsecase,
        observeProfileNameUsecase: ObserveProfileNameUsecase
    ): DashboardViewModel {
        return DashboardViewModel(
            dispatcher = dispatcher,
            profileNameUsecase = profileNameUsecase,
            observeProfileNameUsecase = observeProfileNameUsecase
        )
    }

    class Builder(scope: Scope): UiComponent.ComponentBuilder(scope) {
        override fun build(): Scope {
            val scope = scope(named<Dashboard>())
            scope.getKoin().loadModules(listOf(
                module { scope<Dashboard> {
                    factory<UiComponentProvider.Factory> {
                        UiBuilderFactory(
                            listOf(
                                this@Builder,
                                BiometricCapture.Builder(scope),
                            )
                        )
                    }
                    }
                },
                Dashboard.module
            ))
            return scope
        }
    }
}
