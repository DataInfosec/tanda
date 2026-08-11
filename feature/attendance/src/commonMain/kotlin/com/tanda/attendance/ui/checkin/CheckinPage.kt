package com.tanda.attendance.ui.checkin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tanda.biometrics.domain.model.Capture
import com.tanda.biometrics.domain.model.Image
import com.tanda.biometrics.domain.model.Mode
import com.tanda.biometrics.ui.fingerprint.FingerprintViewModel
import com.tanda.core.ui.design.DesignButton
import com.tanda.core.ui.design.DesignMotion
import com.tanda.core.ui.design.DesignText
import org.jetbrains.compose.resources.stringResource
import tanda.feature.attendance.generated.resources.Res
import tanda.feature.attendance.generated.resources.checkin

@Composable
fun CheckinPage(
    capture: State<Capture?>,
    mode: State<Mode>,
    status: State<FingerprintViewModel.Status>,
    isLoading: State<Boolean>,
    enabled: State<Boolean>,
    onCheckin: () -> Unit
) {
    val handleCheckin by rememberUpdatedState(onCheckin)
    Box(modifier = Modifier.fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .imePadding()
        .padding(horizontal = 20.dp)
        .padding(vertical = 16.dp)) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (mode.value is Mode.Platen) {
                DesignText("Place your finger on the platen to check in")
            }
            when (status.value) {
                is FingerprintViewModel.Status.Default -> {}
                is FingerprintViewModel.Status.Capture -> {
                    val img: Image = (status.value as FingerprintViewModel.Status.Capture).image
                    DesignText(text = "Captured image: ${img.width}x${img.height} (${img.data.size} bytes)")
                    ImagePreview(image = img)
                }
            }
            DesignMotion(targetState = capture.value) { capture ->
                if (capture != null) {
                    DesignText(text = "Captured details: ${capture.id}x${capture.score}")
                }
            }
        }
        DesignButton(
            onClick = { handleCheckin() },
            isLoading = isLoading.value,
            enabled = !isLoading.value && enabled.value,
            modifier = Modifier.fillMaxWidth()
                .align(Alignment.BottomCenter),
        ) { DesignText(stringResource(Res.string.checkin)) }
    }
}

@Composable
private fun ImagePreview(image: Image) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Image Preview",
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = "Size: ${image.width} × ${image.height}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "Bytes: ${image.data.size}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
