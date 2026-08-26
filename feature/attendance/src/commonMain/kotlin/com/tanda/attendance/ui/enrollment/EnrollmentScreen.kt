package com.tanda.attendance.ui.enrollment

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.tanda.attendance.ui.enrollment.EnrollmentEvent.Companion.LocalEnrollmentEvent
import com.tanda.biometrics.domain.model.Mode
import com.tanda.biometrics.ui.fingerprint.FingerprintViewModel
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.core.ui.design.DesignStreamState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.Serializable
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.scope.ScopeID

@Serializable
data class ScanRoute(val id: String)

@Composable
fun EnrollmentScreen(
    scope: ScopeID,
    deviceId: Int
) {
    val factory = getKoin().getScope(scope).get<UiComponentProvider.Factory>()
    val component = remember { factory.builder(Enrollment.Builder::class).build() }
    val viewModel: EnrollmentViewModel = koinViewModel(scope = component)
    val state = viewModel.state.collectAsStateWithLifecycle()
    val processing = remember { derivedStateOf { state.value is EnrollmentViewModel.State.Loading } }
    val identifier = remember { TextFieldState() }
    val controller = rememberNavController()
    val localEvent = LocalEnrollmentEvent.current
    val status = localEvent.viewModel.status.collectAsStateWithLifecycle()
    val scannerState = localEvent.viewModel.state.collectAsStateWithLifecycle()
    val mode = localEvent.viewModel.mode.collectAsStateWithLifecycle()
    val isInitialized = remember { derivedStateOf {
        scannerState.value is FingerprintViewModel.State.Initialized
    } }
    val isLoading = remember { derivedStateOf { localEvent.stream.value is DesignStreamState.Loading } }
    val snackbar = remember { SnackbarHostState() }
    NavHost(
        navController = controller,
        startDestination = "enroll"
    ) {
        composable("enroll") {
            EnrollmentPage(
                identifier = identifier,
                isLoading = isLoading
            ) { localEvent.viewModel(deviceId, 0) }
        }
        composable<ScanRoute> { backStackEntry ->
            Scaffold(snackbarHost = { SnackbarHost(snackbar) }) {
                EnrollmentScanner(
                    identifier = backStackEntry.toRoute<ScanRoute>().id,
                    mode = mode,
                    status = status,
                    processing = processing
                ) { identifier, image -> viewModel(identifier, image) }
            }
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { mode.value to isInitialized.value }
            .collectLatest { value ->
                if (value.first is Mode.Platen && value.second) {
                    controller.navigate(ScanRoute(identifier.text.toString())) {
                        popUpTo("enroll") {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }
            }
    }
    LaunchedEffect(state.value) {
        val message = when (val current = state.value) {
            EnrollmentViewModel.State.Default -> null
            EnrollmentViewModel.State.Loading -> "Processing..."
            is EnrollmentViewModel.State.Success -> "Enrollment successful"
            is EnrollmentViewModel.State.Error -> {
                current.error.message ?: "Something went wrong"
            }
        }
        message?.let { snackbar.showSnackbar(it) }
    }
}
