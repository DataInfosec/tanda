package com.tanda.campus.ui.dashboard

import androidx.navigation.NavController
import com.tanda.biometrics.ui.capture.CaptureEvent

class DashboardInteractor(
    private val controller: NavController
) : CaptureEvent {
    override fun invoke(event: CaptureEvent.Event) {
        when(event) {
            is CaptureEvent.Event.Enroll -> controller.navigate("subject")
            is CaptureEvent.Event.Profile -> controller.navigate("profile")
        }
    }
}
