package com.tanda.attendance.ui.enrollment

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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tanda.biometrics.domain.model.Image
import com.tanda.biometrics.domain.model.Mode
import com.tanda.biometrics.ui.fingerprint.FingerprintViewModel
import com.tanda.core.ui.design.DesignButton
import com.tanda.core.ui.design.DesignText
import org.jetbrains.compose.resources.stringResource
import org.koin.ext.getFullName
import tanda.feature.attendance.generated.resources.Res
import tanda.feature.attendance.generated.resources.enrol

@Composable
fun EnrollmentScanner(
    identifier: String,
    mode: State<Mode>,
    processing: State<Boolean>,
    status: State<FingerprintViewModel.Status>,
    onScan: (String, Image) -> Unit
) {
    val handleScan by rememberUpdatedState(onScan)
    val isValid = remember { derivedStateOf {
        status.value is FingerprintViewModel.Status.Capture && !processing.value
    } }
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
            when (status.value) {
                is FingerprintViewModel.Status.Default -> {
                    DesignText("Place your finger to scan (${mode.value::class.simpleName})")
                }
                is FingerprintViewModel.Status.Capture -> {
                    val img: Image = (status.value as FingerprintViewModel.Status.Capture).image
                    DesignText(text = "Captured image: ${img.width}x${img.height} (${img.data.size} bytes)")
                    ImagePreview(image = img)
                }
            }
        }
        DesignButton(
            onClick = {
                (status.value as? FingerprintViewModel.Status.Capture?)?.let {
                    handleScan(identifier, it.image)
                }
            },
            isLoading = processing.value,
            enabled = isValid.value,
            modifier = Modifier.fillMaxWidth()
                .align(Alignment.BottomCenter),
        ) { DesignText(stringResource(Res.string.enrol)) }
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
