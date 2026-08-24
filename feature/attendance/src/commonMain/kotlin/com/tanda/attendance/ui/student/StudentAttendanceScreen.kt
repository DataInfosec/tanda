package com.tanda.attendance.ui.student

import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tanda.attendance.domain.model.AttendanceAction
import com.tanda.biometrics.ui.fingerprint.FingerprintCaptureScreen
import com.tanda.core.ui.component.UiComponentProvider
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.scope.ScopeID
import tanda.feature.attendance.generated.resources.Res
import tanda.feature.attendance.generated.resources.attendance_recorded

@Composable
fun StudentAttendanceScreen(
    scope: ScopeID,
    deviceId: Int,
    deviceIndex: Int,
    onBackClick: () -> Unit,
) {
    val factory = getKoin().getScope(scope).get<UiComponentProvider.Factory>()
    val component = remember { factory.builder(StudentAttendance.Builder::class).build() }
    val viewModel: StudentAttendanceViewModel = koinViewModel(scope = component)
    val captureViewModel: StudentAttendanceCaptureViewModel = koinViewModel(scope = component)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val captureState by captureViewModel.state.collectAsStateWithLifecycle()
    val controller = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val successMessage = stringResource(Res.string.attendance_recorded)
    var selectedPointId by remember { mutableStateOf<String?>(null) }
    var selectedAction by remember { mutableStateOf<AttendanceAction?>(null) }

    LaunchedEffect(Unit) { viewModel.start() }
    LaunchedEffect(captureState) {
        if (captureState is StudentAttendanceCaptureViewModel.State.Success) {
            captureViewModel.reset()
            snackbar.showSnackbar(successMessage)
            controller.popBackStack()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) {
        NavHost(
            navController = controller,
            startDestination = StudentAttendanceRoute.Selection,
        ) {
            composable(StudentAttendanceRoute.Selection) {
                StudentAttendancePage(
                    state = state,
                    onBackClick = onBackClick,
                    onRetryClick = viewModel::refresh,
                    onOptionClick = { option ->
                        selectedPointId = option.point.id
                        selectedAction = option.action
                        captureViewModel.reset()
                        controller.navigate(StudentAttendanceRoute.Capture)
                    },
                )
            }
            composable(StudentAttendanceRoute.Capture) {
                val pointId = selectedPointId
                val action = selectedAction
                if (pointId == null || action == null) {
                    LaunchedEffect(Unit) { controller.popBackStack() }
                    return@composable
                }
                val errorMessage = (captureState as? StudentAttendanceCaptureViewModel.State.Error)
                    ?.message
                FingerprintCaptureScreen(
                    scope = component.id,
                    deviceId = deviceId,
                    deviceIndex = deviceIndex,
                    processing = captureState is StudentAttendanceCaptureViewModel.State.Processing,
                    errorMessage = errorMessage,
                    onBackClick = { controller.popBackStack() },
                    onCancelClick = { controller.popBackStack() },
                    onTryAgainClick = captureViewModel::reset,
                    onContinueClick = { image ->
                        captureViewModel.record(pointId, action, image)
                    },
                )
            }
        }
    }
}

private object StudentAttendanceRoute {
    const val Selection = "student_attendance_selection"
    const val Capture = "student_attendance_capture"
}
