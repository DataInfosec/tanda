package com.tanda.ui.home

import com.tanda.attendance.ui.console.Console
import com.tanda.core.ui.component.UiComponent
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.core.ui.factory.UiBuilderFactory
import org.koin.core.annotation.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module
import org.koin.ksp.generated.module

@Module
object Home {
    class Builder(scope: Scope): UiComponent.ComponentBuilder(scope) {
        override fun build(): Scope {
            val scope = scope(named<Home>())
            scope.getKoin().loadModules(listOf(
                module {
                    scope<Home> {
                        factory<UiComponentProvider.Factory> {
                            UiBuilderFactory(
                                listOf(
                                    this@Builder,
                                    Console.Builder(scope),
                                )
                            )
                        }
                    }
                },
                Home.module
            ))
            return scope
        }
    }
}
