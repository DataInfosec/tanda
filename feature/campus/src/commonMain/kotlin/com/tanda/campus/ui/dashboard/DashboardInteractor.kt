package com.tanda.campus.ui.dashboard

import androidx.navigation.NavController
import com.tanda.biometrics.ui.capture.CaptureEvent
import com.tanda.biometrics.ui.subject.SubjectEvent

class DashboardInteractor(
    private val controller: NavController
) : CaptureEvent, SubjectEvent {
    override fun invoke(event: CaptureEvent.Event) {
        when(event) {
            is CaptureEvent.Event.Enroll -> controller.navigate("subject")
        }
    }

    override fun invoke(event: SubjectEvent.Event) {}
}
