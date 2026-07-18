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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanda.core.ui.extension.factory
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.scope.ScopeID

@Composable
fun ScannerScreen(scope: ScopeID) {
    val factory = factory(scope)
    val component = remember { factory.builder(Scanner.Builder::class).build() }
    val viewModel: ScannerViewModel = koinViewModel(scope = component)
    val status = viewModel.status.collectAsStateWithLifecycle()
    val state = viewModel.state.collectAsStateWithLifecycle()

    // simple local controls for device id and finger index
    var deviceId by remember { mutableStateOf(0) }
    var index by remember { mutableStateOf(0) }

    Surface(modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Scanner", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))

            // Status display
            Surface(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface)
            {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "Mode: ${status.value.mode::class.simpleName}", style = MaterialTheme.typography.titleMedium)
                    when (val s = status.value) {
                        is ScannerViewModel.Status.Default -> Text(text = "Ready")
                        is ScannerViewModel.Status.Capture -> Text(text = "Captured")
                    }
                }
            }

            // State display and spinner
            Surface(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface)
            {
                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "State: ${state.value::class.simpleName}", style = MaterialTheme.typography.titleMedium)
                    if (state.value is ScannerViewModel.State.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Controls: device id and index
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { if (deviceId > 0) deviceId-- }, modifier = Modifier.height(40.dp)) { Text("-") }
                    Text(text = " Device: $deviceId ", modifier = Modifier.padding(horizontal = 8.dp))
                    Button(onClick = { deviceId++ }, modifier = Modifier.height(40.dp)) { Text("+") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { if (index > 0) index-- }, modifier = Modifier.height(40.dp)) { Text("-") }
                    Text(text = " Index: $index ", modifier = Modifier.padding(horizontal = 8.dp))
                    Button(onClick = { index++ }, modifier = Modifier.height(40.dp)) { Text("+") }
                }
            }

            // Capture button
            Button(onClick = { viewModel(deviceId, index) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(text = "Capture")
            }
        }
    }
}
