package com.tanda.attendance.ui.checkin

import com.tanda.biometrics.ui.scanner.Scanner
import com.tanda.core.ui.component.UiComponent
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.core.ui.factory.UiBuilderFactory
import org.koin.core.annotation.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module
import org.koin.ksp.generated.module

@Module
object Checkin {
    class Builder(scope: Scope): UiComponent.ComponentBuilder(scope) {
        override fun build(): Scope {
            val scope = scope(named<Checkin>())
            scope.getKoin().loadModules(listOf(
                module {
                    scope<Checkin> {
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
                Checkin.module
            ))
            return scope
        }
    }
}
