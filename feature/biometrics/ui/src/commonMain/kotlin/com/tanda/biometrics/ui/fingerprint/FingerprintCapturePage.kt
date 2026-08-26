package com.tanda.biometrics.ui.fingerprint

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanda.biometrics.ui.capture.BiometricCaptureHeader
import com.tanda.core.ui.design.DesignButton
import com.tanda.core.ui.design.DesignOutlineButton
import com.tanda.core.ui.design.DesignText
import com.tanda.core.ui.extension.designScheme
import com.tanda.core.ui.theme.DesignTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import tanda.feature.biometrics.ui.generated.resources.Res
import tanda.feature.biometrics.ui.generated.resources.ic_fingerprint

sealed interface FingerprintCaptureUiState {
    data object Idle : FingerprintCaptureUiState
    data object Capturing : FingerprintCaptureUiState
    data object Success : FingerprintCaptureUiState
    data class Error(val message: String = "Finger capture unsuccessful") : FingerprintCaptureUiState
}

@Composable
fun FingerprintCapturePage(
    state: FingerprintCaptureUiState,
    modifier: Modifier = Modifier,
    processing: Boolean = false,
    onBackClick: () -> Unit = {},
    onCancelClick: () -> Unit = {},
    onTryAgainClick: () -> Unit = {},
    onContinueClick: () -> Unit = {}
) {
    val handleBackClick by rememberUpdatedState(onBackClick)
    val handleCancelClick by rememberUpdatedState(onCancelClick)
    val handleTryAgainClick by rememberUpdatedState(onTryAgainClick)
    val handleContinueClick by rememberUpdatedState(onContinueClick)
    val isSuccessful = state is FingerprintCaptureUiState.Success
    val isCapturing = state is FingerprintCaptureUiState.Capturing
    val statusColor = when (state) {
        is FingerprintCaptureUiState.Error -> MaterialTheme.colorScheme.error
        FingerprintCaptureUiState.Success -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.designScheme.text
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        BiometricCaptureHeader(
            onBackClicked = { handleBackClick() },
            text = "Fingerprint"
        )

        Spacer(modifier = Modifier.height(72.dp))

        DesignText(
            text = "Fingerprint capture",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        DesignText(
            text = "Request user to place index finger on\nthe fingerprint scanner.",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center
            ),
            color = MaterialTheme.designScheme.text,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.weight(1f))

        if (state is FingerprintCaptureUiState.Error) {
            DesignText(
                text = state.message,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                ),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(28.dp))
        }

        Icon(
            painter = painterResource(Res.drawable.ic_fingerprint),
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier
                .size(96.dp)
                .align(Alignment.CenterHorizontally)
        )

        if (state is FingerprintCaptureUiState.Error) {
            Spacer(modifier = Modifier.height(28.dp))

            DesignButton(
                onClick = { handleTryAgainClick() },
                colors = com.tanda.core.ui.design.designTertiaryButtonColors(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                minHeight = 40.dp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                DesignText(
                    text = "Try again",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DesignOutlineButton(
                text = "Cancel",
                onClick = { handleCancelClick() },
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            )

            DesignButton(
                onClick = { handleContinueClick() },
                enabled = isSuccessful && !processing,
                isLoading = isCapturing || processing,
                shape = RoundedCornerShape(5.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                minHeight = 60.dp,
                modifier = Modifier.weight(1f)
            ) {
                DesignText(
                    text = if (state is FingerprintCaptureUiState.Idle) "Save" else "Continue"
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewFingerprintCaptureIdle() {
    DesignTheme(darkTheme = false) {
        FingerprintCapturePage(state = FingerprintCaptureUiState.Idle)
    }
}

@Preview
@Composable
private fun PreviewFingerprintCaptureError() {
    DesignTheme(darkTheme = false) {
        FingerprintCapturePage(
            state = FingerprintCaptureUiState.Error()
        )
    }
}

@Preview
@Composable
private fun PreviewFingerprintCaptureSuccess() {
    DesignTheme(darkTheme = false) {
        FingerprintCapturePage(state = FingerprintCaptureUiState.Success)
    }
}
