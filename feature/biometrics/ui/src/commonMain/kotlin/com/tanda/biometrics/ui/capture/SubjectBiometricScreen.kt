package com.tanda.biometrics.ui.capture

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanda.biometrics.domain.model.Subject
import com.tanda.biometrics.ui.fingerprint.FingerprintCaptureScreen
import com.tanda.core.ui.component.UiComponentProvider
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.scope.ScopeID

@Composable
fun SubjectBiometricScreen(
    scope: ScopeID,
    config: SubjectBiometricUiConfig,
    deviceId: Int,
    deviceIndex: Int = 0,
    onBackClick: () -> Unit = {}
) {
    val factory = getKoin().getScope(scope).get<UiComponentProvider.Factory>()
    val component = remember { factory.builder(BiometricCapture.Builder::class).build() }
    val viewModel: SubjectBiometricViewModel = koinViewModel(scope = component)
    val state = viewModel.state.collectAsStateWithLifecycle()
    val enrollmentState = viewModel.enrollmentState.collectAsStateWithLifecycle()
    val subjectID = remember { TextFieldState() }
    val searchQuery = remember { TextFieldState() }
    val snackbar = remember { SnackbarHostState() }
    var captureSubject by remember { mutableStateOf<Subject?>(null) }
    var fingerprintError by remember { mutableStateOf<String?>(null) }
    val isLoading: State<Boolean> = remember {
        derivedStateOf { state.value is SubjectBiometricViewModel.State.Loading }
    }
    val isEnrollmentLoading: State<Boolean> = remember {
        derivedStateOf { enrollmentState.value is SubjectBiometricViewModel.EnrollmentState.Loading }
    }
    val error: State<String?> = remember {
        derivedStateOf { (state.value as? SubjectBiometricViewModel.State.Error)?.message }
    }
    val subject = (state.value as? SubjectBiometricViewModel.State.Success)?.subject

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) }
    ) {
        val activeCaptureSubject = captureSubject
        if (activeCaptureSubject != null) {
            FingerprintCaptureScreen(
                scope = component.id,
                deviceId = deviceId,
                deviceIndex = deviceIndex,
                processing = isEnrollmentLoading.value,
                errorMessage = fingerprintError,
                onBackClick = {
                    fingerprintError = null
                    captureSubject = null
                },
                onCancelClick = {
                    fingerprintError = null
                    captureSubject = null
                },
                onTryAgainClick = { fingerprintError = null },
                onContinueClick = { image ->
                    fingerprintError = null
                    viewModel.enrollSubjectFingerprint(activeCaptureSubject.id, image)
                }
            )
        } else if (subject != null) {
            SubjectDetailPage(
                title = config.detailTitle,
                subject = subject,
                onBackClick = onBackClick,
                onCaptureBiometric = { captureSubject = subject },
                onUseAnotherId = {
                    viewModel.reset()
                }
            )
        } else {
            SubjectEnrolmentPage(
                config = config,
                subjectID = subjectID,
                searchQuery = searchQuery,
                isLoading = isLoading,
                error = error,
                onBackClick = onBackClick,
                onContinue = {
                    viewModel.readSubject(
                        externalReference = subjectID.text.toString().trim(),
                        expectedSubjectType = config.expectedSubjectType,
                    )
                }
            )
        }
    }

    LaunchedEffect(enrollmentState.value) {
        when (val current = enrollmentState.value) {
            SubjectBiometricViewModel.EnrollmentState.Default -> Unit
            SubjectBiometricViewModel.EnrollmentState.Loading -> {
                snackbar.showSnackbar("Processing...")
            }
            SubjectBiometricViewModel.EnrollmentState.Success -> {
                snackbar.showSnackbar("Enrollment successful")
                fingerprintError = null
                captureSubject = null
                viewModel.reset()
                viewModel.resetEnrollment()
            }
            is SubjectBiometricViewModel.EnrollmentState.Error -> {
                fingerprintError = current.message
                snackbar.showSnackbar(current.message)
                viewModel.resetEnrollment()
            }
        }
    }
}
