package com.tanda.attendance.ui.attendance

import androidx.compose.runtime.staticCompositionLocalOf
import org.koin.ext.getFullName

interface AttendanceEvent {
    operator fun invoke(event: Event)

    sealed interface Event {
        data object Console : Event
    }

    companion object {
        val LocalAttendanceEvent = staticCompositionLocalOf<AttendanceEvent> {
            error("${AttendanceEvent::class.getFullName()} not provided")
        }
    }
}
