package com.tanda.biometrics.ui.capture

import com.tanda.core.ui.component.UiComponent
import org.koin.core.annotation.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module
import org.koin.ksp.generated.module

@Module
object Capture {
    class Builder(scope: Scope): UiComponent.ComponentBuilder(scope) {
        override fun build(): Scope {
            val scope = scope(named<Capture>())
            scope.getKoin().loadModules(listOf(
                module {
                    scope<Capture> {

                    }
                },
                Capture.module
            ))
            return scope
        }
    }
}
