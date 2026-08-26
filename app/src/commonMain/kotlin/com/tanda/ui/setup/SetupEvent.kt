package com.tanda.ui.setup

import androidx.compose.runtime.staticCompositionLocalOf
import org.koin.ext.getFullName

interface SetupEvent {
    operator fun invoke(event: Event)

    sealed interface Event {
        data object Complete : Event
    }

    companion object {
        val LocalSetupEvent = staticCompositionLocalOf<SetupEvent> {
            error("${SetupEvent::class.getFullName()} not provided")
        }
    }
}
