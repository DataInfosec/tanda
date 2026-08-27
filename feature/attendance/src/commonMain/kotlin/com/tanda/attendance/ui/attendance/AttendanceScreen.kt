package com.tanda.attendance.ui.attendance

import androidx.compose.runtime.Composable
import com.tanda.attendance.ui.attendance.AttendanceEvent.Companion.LocalAttendanceEvent
import org.koin.core.scope.ScopeID

@Composable
fun AttendanceScreen(scope: ScopeID) {
    val localEvent = LocalAttendanceEvent.current
    AttendancePage { localEvent(AttendanceEvent.Event.Console) }
}
