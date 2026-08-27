package com.tanda.ui.home

import androidx.navigation.NavController
import com.tanda.attendance.ui.attendance.AttendanceEvent

class HomeInteractor(
    private val controller: NavController
) : AttendanceEvent {
    override fun invoke(event: AttendanceEvent.Event) {
        when(event) {
            is AttendanceEvent.Event.Console -> controller.navigate("console")
        }
    }
}
