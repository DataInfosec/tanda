package com.tanda.core.ui.component

import com.tanda.core.ui.extension.scopeOf
import org.koin.core.qualifier.TypeQualifier
import org.koin.core.scope.Scope

interface UiComponent {
    interface Builder

    abstract class ComponentBuilder(private val scope: Scope) : Builder {
        fun scope(qualifier: TypeQualifier): Scope {
            return scope.getKoin().scopeOf(qualifier).also { it.linkTo(scope) }
        }

        abstract fun build(): Scope
    }

    abstract class InteractableComponentBuilder<T>(
        protected val scope: Scope,
        protected val interactor: T
    ) : ComponentBuilder(scope)

    abstract class ComponentBuilderWithArgs<T>(protected val scope: Scope) : Builder {
        fun scope(qualifier: TypeQualifier): Scope {
            return scope.getKoin().scopeOf(qualifier).also { it.linkTo(scope) }
        }

        abstract fun build(args: T): Scope
    }
}
