package com.tanda.ui.setup

import androidx.compose.runtime.Composable
import com.tanda.ui.setup.SetupEvent.Companion.LocalSetupEvent
import org.koin.core.scope.ScopeID

@Composable
fun SetupScreen(scope: ScopeID) {
    val localEvent = LocalSetupEvent.current

    SetupPage(
        onDismissClick = { localEvent(SetupEvent.Event.Dismiss) },
        onContinueClick = { id, token -> localEvent(SetupEvent.Event.Complete(id, token)) }
    )
}
