package com.tanda.core.ui.extension

import androidx.compose.runtime.Composable
import com.tanda.core.ui.component.UiComponentProvider
import org.koin.compose.getKoin

@Composable
fun factory(scope: String): UiComponentProvider.Factory {
    return get<UiComponentProvider.Factory>(scope)
}

@Composable
inline fun<reified T : Any> get(scope: String): T {
    return getKoin().getScope(scope).get<T>()
}
