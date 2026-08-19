package com.tanda.biometrics.ui.fingerprint

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanda.biometrics.domain.model.Image
import com.tanda.core.ui.design.DesignStreamState
import org.koin.core.scope.ScopeID

@Composable
fun FingerprintCaptureScreen(
    scope: ScopeID,
    deviceId: Int,
    deviceIndex: Int = 0,
    autoStart: Boolean = true,
    processing: Boolean = false,
    errorMessage: String? = null,
    onBackClick: () -> Unit = {},
    onCancelClick: () -> Unit = {},
    onTryAgainClick: () -> Unit = {},
    onContinueClick: (Image) -> Unit = {}
) {
    FingerprintScreen(
        scope = scope,
        deviceId = deviceId
    ) { viewModel, stream ->
        val status = viewModel.status.collectAsStateWithLifecycle()
        val uiState: State<FingerprintCaptureUiState> = remember(errorMessage) {
            derivedStateOf {
                when {
                    errorMessage != null -> FingerprintCaptureUiState.Error(errorMessage)
                    stream.value is DesignStreamState.Error -> {
                        val error = (stream.value as DesignStreamState.Error).error
                        FingerprintCaptureUiState.Error(
                            error.message ?: "Finger capture unsuccessful"
                        )
                    }
                    stream.value is DesignStreamState.Loading -> FingerprintCaptureUiState.Capturing
                    status.value is FingerprintViewModel.Status.Capture -> FingerprintCaptureUiState.Success
                    else -> FingerprintCaptureUiState.Idle
                }
            }
        }

        LaunchedEffect(deviceId, deviceIndex, autoStart) {
            if (autoStart) {
                viewModel(deviceId, deviceIndex)
            }
        }

        FingerprintCapturePage(
            state = uiState.value,
            processing = processing,
            onBackClick = onBackClick,
            onCancelClick = onCancelClick,
            onTryAgainClick = {
                onTryAgainClick()
                viewModel(deviceId, deviceIndex)
            },
            onContinueClick = {
                (status.value as? FingerprintViewModel.Status.Capture)?.let {
                    onContinueClick(it.image)
                }
            }
        )
    }
}
