package com.tanda.ui.main

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
                                    Scanner.Builder(scope),
                                )
                            )
                        }
                    }
                },
                AppModule.module,
                Main.module
            ))
            return scope
        }
    }
}
