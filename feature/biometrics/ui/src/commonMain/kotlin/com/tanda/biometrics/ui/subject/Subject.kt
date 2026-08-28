package com.tanda.biometrics.ui.subject

import com.tanda.biometrics.domain.usecase.SubjectUsecase
import com.tanda.core.common.concurrent.Dispatcher
import com.tanda.core.ui.component.UiComponent
import org.koin.core.annotation.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module
import org.koin.ksp.generated.*

@Module
object Subject {
    @org.koin.core.annotation.Scope(Subject::class)
    fun provideViewModel(
        dispatcher: Dispatcher,
        usecase: SubjectUsecase
    ): SubjectViewModel {
        return SubjectViewModel(
            dispatcher = dispatcher,
            usecase = usecase,
        )
    }

    class Builder(scope: Scope): UiComponent.ComponentBuilder(scope) {
        override fun build(): Scope {
            val scope = scope(named<Subject>())
            scope.getKoin().loadModules(listOf(
                    module { scope<Subject> { } },
                Subject.module
            ))
            return scope
        }
    }
}
