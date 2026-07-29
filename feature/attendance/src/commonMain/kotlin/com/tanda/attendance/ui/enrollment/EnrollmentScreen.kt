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
import com.tanda.biometrics.domain.model.Mode
import com.tanda.biometrics.ui.fingerprint.FingerprintScreen
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
    FingerprintScreen(component.id, deviceId) { vm, stream ->
        val status = vm.status.collectAsStateWithLifecycle()
        val mode = remember { derivedStateOf { status.value.mode } }
        val isLoading = remember { derivedStateOf { stream.value is DesignStreamState.Loading } }
        val snackbarHostState = remember { SnackbarHostState() }
        NavHost(
            navController = controller,
            startDestination = "enroll"
        ) {
            composable("enroll") {
                EnrollmentPage(
                    identifier = identifier,
                    isLoading = isLoading
                ) { vm(deviceId, 0) }
            }
            composable<ScanRoute> { backStackEntry ->
                Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) {
                    EnrollmentScanner(
                        identifier = backStackEntry.toRoute<ScanRoute>().id,
                        status = status,
                        processing = processing
                    ) { identifier, image ->
                        viewModel(identifier, image)
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            snapshotFlow { mode.value }
                .collectLatest {
                    if (it is Mode.Platen) {
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
            message?.let {
                snackbarHostState.showSnackbar(it)
            }
        }
    }
}
