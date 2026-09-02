package com.tanda.ui.splash

import androidx.compose.runtime.staticCompositionLocalOf
import org.koin.ext.getFullName

interface SplashEvent {
    suspend fun initialized(): Boolean

    operator fun invoke(event: Event)

    sealed interface Event {
        data object Home : Event
        data object Setup : Event
        data object Login : Event
    }

    companion object {
        val LocalSplashEvent = staticCompositionLocalOf<SplashEvent> {
            error("${SplashEvent::class.getFullName()} not provided")
        }
    }
}
