package com.tanda.biometrics.ui.capture

import androidx.compose.runtime.Composable
import com.tanda.biometrics.ui.capture.CaptureEvent.Companion.LocalCaptureEvent
import org.koin.core.scope.ScopeID

@Composable
fun CaptureScreen(scope: ScopeID) {
    val localEvent = LocalCaptureEvent.current
    CaptureOption(onStudentBiometricCapture = { localEvent(CaptureEvent.Event.Enroll) })
}
