package com.tanda.biometrics.ui.capture

import androidx.compose.runtime.Composable
import org.koin.core.scope.ScopeID

@Composable
fun BiometricCaptureScreen(
    scope: ScopeID,
    onStaffBiometricCapture: () -> Unit = {},
    onStudentBiometricCapture: () -> Unit = {},
    onBackClicked: () -> Unit = {}
) {
    BiometricCapturePage(
        onStaffBiometricCapture = onStaffBiometricCapture,
        onStudentBiometricCapture = onStudentBiometricCapture,
        onBackClicked = onBackClicked
    )
}
