package com.tanda.attendance.ui.checkin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tanda.core.ui.component.UiComponentProvider
import org.koin.compose.getKoin
import org.koin.core.scope.ScopeID

@Composable
fun CheckinScreen(scope: ScopeID) {
    val factory = getKoin().getScope(scope).get<UiComponentProvider.Factory>()
    val component = remember { factory.builder(Checkin.Builder::class).build() }
}
