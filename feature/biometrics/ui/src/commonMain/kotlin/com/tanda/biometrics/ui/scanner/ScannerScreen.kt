package com.tanda.biometrics.ui.scanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.biometrics.domain.model.Image
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.scope.ScopeID

@Composable
fun ScannerScreen(
    scope: ScopeID,
    deviceId: Int,
    index: Int = 0
) {
    val factory = getKoin().getScope(scope).get<UiComponentProvider.Factory>()
    val component = remember { factory.builder(Scanner.Builder::class).build() }
    val viewModel: ScannerViewModel = koinViewModel(scope = component)
    val status = viewModel.status.collectAsStateWithLifecycle()
    val state = viewModel.state.collectAsStateWithLifecycle()
    val isLoading = remember { derivedStateOf { state.value is ScannerViewModel.State.Loading } }
    Surface(
        modifier = Modifier.padding(16.dp),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status display
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Mode: ${status.value.mode::class.simpleName}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    when (status.value) {
                        is ScannerViewModel.Status.Default -> {}
                        is ScannerViewModel.Status.Capture -> {
                            val img: Image = (status.value as ScannerViewModel.Status.Capture).image
                            Text(text = "Captured image: ${img.width}x${img.height} (${img.data.size} bytes)")
                            ImagePreview(image = img)
                        }
                    }
                }
            }

            // State display and spinner
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "State: ${state.value::class.simpleName}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (isLoading.value) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Capture button
            Button(
                onClick = { viewModel(deviceId, index) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = !isLoading.value
            ) { Text(text = "Capture") }
        }
    }
    LaunchedEffect(deviceId) {
        viewModel(deviceId)
    }
}

@Composable
private fun ImagePreview(image: Image) {
    // Display image info
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Image Preview", style = MaterialTheme.typography.labelSmall)
            Text(text = "Size: ${image.width} × ${image.height}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            Text(text = "Bytes: ${image.data.size}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

