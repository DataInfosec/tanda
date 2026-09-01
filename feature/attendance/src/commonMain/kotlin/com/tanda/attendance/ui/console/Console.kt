package com.tanda.attendance.ui.console

import com.tanda.attendance.ui.checkin.Checkin
import com.tanda.attendance.ui.enrollment.Enrollment
import com.tanda.biometrics.ui.scanner.Scanner
import com.tanda.core.ui.component.UiComponent
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.core.ui.factory.UiBuilderFactory
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module

object Console {
    class Builder(scope: Scope): UiComponent.ComponentBuilder(scope) {
        override fun build(): Scope {
            val scope = scope(named<Console>())
            scope.getKoin().loadModules(listOf(
                module {
                    scope<Console> {
                        factory<UiComponentProvider.Factory> {
                            UiBuilderFactory(
                                listOf(
                                    this@Builder,
                                    Scanner.Builder(scope),
                                    Checkin.Builder(scope),
                                    Enrollment.Builder(scope),
                                )
                            )
                        }
                    }
                }
            ))
            return scope
        }
    }
}
