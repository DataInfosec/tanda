package com.tanda.account.ui.login

import androidx.compose.runtime.staticCompositionLocalOf
import org.koin.ext.getFullName

interface LoginEvent {
    operator fun invoke(event: Event)

    sealed interface Event {
        data object Home : Event
    }

    companion object {
        val LocalLoginEvent = staticCompositionLocalOf<LoginEvent> {
            error("${LoginEvent::class.getFullName()} not provided")
        }
    }
}
