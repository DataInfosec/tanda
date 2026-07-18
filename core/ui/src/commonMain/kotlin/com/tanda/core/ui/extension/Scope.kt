package com.tanda.core.ui.extension

import org.koin.core.Koin
import org.koin.core.component.getScopeId
import org.koin.core.qualifier.TypeQualifier
import org.koin.core.scope.Scope

fun Koin.scopeOf(qualifier: TypeQualifier): Scope {
    return getScopeOrNull(qualifier.getScopeId())
        ?: createScope(qualifier.getScopeId(), qualifier)
}
