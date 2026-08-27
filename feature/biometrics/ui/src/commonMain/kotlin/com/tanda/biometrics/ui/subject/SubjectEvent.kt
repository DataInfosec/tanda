package com.tanda.biometrics.ui.subject

import androidx.compose.runtime.staticCompositionLocalOf
import org.koin.ext.getFullName

interface SubjectEvent {
    operator fun invoke(event: Event)

    sealed interface Event {
        data object Capture : Event
    }

    companion object {
        val LocalSubjectEvent = staticCompositionLocalOf<SubjectEvent> {
            error("${SubjectEvent::class.getFullName()} not provided")
        }
    }
}
