package com.tanda.ui.home

import androidx.compose.runtime.staticCompositionLocalOf
import org.koin.ext.getFullName

interface HomeEvent {
    operator fun invoke(event: Event)

    sealed interface Event {
        data object Logout : Event
    }

    companion object {
        val LocalHomeEvent = staticCompositionLocalOf<HomeEvent> {
            error("${HomeEvent::class.getFullName()} not provided")
        }
    }
}
