package com.tanda.biometrics.ui.capture

import androidx.compose.runtime.Composable
import org.koin.core.scope.ScopeID

@Composable
fun CaptureScreen(
    scope: ScopeID,
    onStaffBiometricCapture: () -> Unit = {},
    onStudentBiometricCapture: () -> Unit = {},
    onBackClicked: () -> Unit = {}
) {
    CapturePage(
        onStaffBiometricCapture = onStaffBiometricCapture,
        onStudentBiometricCapture = onStudentBiometricCapture,
        onBackClicked = onBackClicked
    )
}
