package com.tanda.attendance.ui.checkin

import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanda.biometrics.domain.exception.FingerprintException
import com.tanda.biometrics.ui.fingerprint.FingerprintScreen
import com.tanda.biometrics.ui.fingerprint.FingerprintViewModel
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.core.ui.design.DesignStreamState
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.scope.ScopeID

@Composable
fun CheckinScreen(
    scope: ScopeID,
    deviceId: Int
) {
    val factory = getKoin().getScope(scope).get<UiComponentProvider.Factory>()
    val component = remember { factory.builder(Checkin.Builder::class).build() }
    val viewModel: CheckinViewModel = koinViewModel(scope = component)
    val state = viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    FingerprintScreen(component.id, deviceId) { vm, stream ->
        val mode = vm.mode.collectAsStateWithLifecycle()
        val status = vm.status.collectAsStateWithLifecycle()
        val capture = remember { derivedStateOf {
            (state.value as? CheckinViewModel.State.Success?)?.capture
        } }
        val enabled = remember { derivedStateOf {
            stream.value is DesignStreamState.Success || stream.value is DesignStreamState.Default
        } }
        val isLoading = remember { derivedStateOf {
            stream.value is DesignStreamState.Loading || state.value is CheckinViewModel.State.Loading
        } }
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) {
            CheckinPage(
                isLoading = isLoading,
                status = status,
                enabled = enabled,
                mode = mode,
                capture = capture
            ) {
                (status.value as? FingerprintViewModel.Status.Capture?)?.let {
                    viewModel(it.image)
                }
            }
        }
        LaunchedEffect(Unit) { vm(deviceId, 0) }
        LaunchedEffect(state.value) {
            val message = when (val current = state.value) {
                CheckinViewModel.State.Default -> null
                CheckinViewModel.State.Loading -> "Processing..."
                is CheckinViewModel.State.Success -> "Check-in successful"
                is CheckinViewModel.State.Error -> {
                    val err = current.error as FingerprintException
                    "${err.message} - ${err.score} ${err.threshold}" ?: "Something went wrong"
                }
            }
            message?.let { snackbar.showSnackbar(it) }
        }
    }
}
