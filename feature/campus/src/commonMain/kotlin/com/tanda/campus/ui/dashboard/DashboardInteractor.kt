package com.tanda.campus.ui.dashboard

import androidx.navigation.NavController
import com.tanda.biometrics.ui.capture.CaptureEvent
import com.tanda.biometrics.ui.subject.SubjectEvent

class DashboardInteractor(
    private val controller: NavController
) : CaptureEvent, SubjectEvent {
    override fun invoke(event: CaptureEvent.Event) {
        when(event) {
            is CaptureEvent.Event.OnBackClick -> controller.popBackStack()
            is CaptureEvent.Event.OnStaffEnrollment -> controller.navigate("staff-subject")
            is CaptureEvent.Event.OnStudentEnrollment -> controller.navigate("student-subject")
        }
    }

    override fun invoke(event: SubjectEvent.Event) {
        when(event) {
            is SubjectEvent.Event.Capture -> {
                //Todo navigate to fingerprint capture screen
            }
        }
    }
}
