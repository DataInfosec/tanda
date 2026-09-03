package com.tanda.biometrics.ui.capture

import androidx.compose.runtime.staticCompositionLocalOf
import org.koin.ext.getFullName

interface CaptureEvent {
    operator fun invoke(event: Event)

    sealed interface Event {
        data object OnBackClick : Event
        data object OnStaffEnrollment : Event
        data object OnStudentEnrollment : Event
    }

    companion object {
        val LocalCaptureEvent = staticCompositionLocalOf<CaptureEvent> {
            error("${CaptureEvent::class.getFullName()} not provided")
        }
    }
}
