package com.tanda.biometrics.ui.capture

import androidx.compose.runtime.staticCompositionLocalOf
import org.koin.ext.getFullName

interface CaptureEvent {
    operator fun invoke(event: Event)

    sealed interface Event {
        data object Enroll : Event
        data object Profile : Event
    }

    companion object {
        val LocalCaptureEvent = staticCompositionLocalOf<CaptureEvent> {
            error("${CaptureEvent::class.getFullName()} not provided")
        }
    }
}
