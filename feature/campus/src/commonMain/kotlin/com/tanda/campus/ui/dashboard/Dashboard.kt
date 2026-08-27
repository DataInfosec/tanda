package com.tanda.campus.ui.dashboard

import com.tanda.account.domain.usecase.AccountUsecase
import com.tanda.biometrics.ui.capture.Capture
import com.tanda.biometrics.ui.subject.Subject
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
        usecase: AccountUsecase
    ): DashboardViewModel {
        return DashboardViewModel(
            dispatcher = dispatcher,
            usecase = usecase
        )
    }

    class Builder(scope: Scope): UiComponent.ComponentBuilder(scope) {
        override fun build(): Scope {
            val scope = scope(named<Dashboard>())
            scope.getKoin().loadModules(listOf(
                module {
                    scope<Dashboard> {
                        factory<UiComponentProvider.Factory> {
                            UiBuilderFactory(
                                listOf(
                                    this@Builder,
                                    Capture.Builder(scope),
                                    Subject.Builder(scope),
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
