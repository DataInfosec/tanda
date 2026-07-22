package com.tanda.biometrics.ui.scanner

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanda.biometrics.domain.model.Status
import com.tanda.biometrics.ui.fingerprint.FingerprintScreen
import com.tanda.core.ui.component.UiComponentProvider
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.scope.ScopeID

@Composable
fun ScannerScreen(scope: ScopeID) {
    val factory = org.koin.compose.getKoin().getScope(scope).get<UiComponentProvider.Factory>()
    val component = remember { factory.builder(Scanner.Builder::class).build() }
    val viewModel: ScannerViewModel = koinViewModel(scope = component)
    val status = viewModel.status.collectAsStateWithLifecycle()
    val state = viewModel.state.collectAsStateWithLifecycle()
    val id = remember { derivedStateOf {
        (status.value as? Status.Attached?)?.id ?:
        (status.value as? Status.Initialize?)?.id
    } }
    val isRunning by remember { derivedStateOf { state.value == ScannerViewModel.State.Start } }
    Scaffold { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                // Title
                Text(
                    text = "Biometrics Scanner",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                // Status Section
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Status",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        StatusDisplay(status = status.value)
                    }
                }

                // State Section
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "State",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        StateDisplay(state = state.value)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Start/Stop Toggle Button
                Button(
                    onClick = {
                        if (isRunning) {
                            viewModel.stop()
                        } else {
                            viewModel.start()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = state.value !is ScannerViewModel.State.Loading
                ) {
                    if (state.value is ScannerViewModel.State.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (isRunning) "Stop" else "Start",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                AnimatedContent(id.value) { target ->
                    target?.let {
                        FingerprintScreen(
                            scope = component.id,
                            deviceId = it
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusDisplay(status: Status) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when (status) {
                is Status.Default -> "Default"
                is Status.Attached -> "Attached (ID: ${status.id})"
                is Status.Detached -> "Detached (ID: ${status.id})"
                is Status.Initialize -> "Initializing (Progress: ${status.progress}%)"
                is Status.Ready -> "Ready (Index: ${status.index})"
                is Status.Error -> "Error: ${(status.error.cause ?: status.error)::class.simpleName}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = when (status) {
                is Status.Default -> MaterialTheme.colorScheme.onSurfaceVariant
                is Status.Attached -> MaterialTheme.colorScheme.primary
                is Status.Detached -> MaterialTheme.colorScheme.onSurfaceVariant
                is Status.Initialize -> MaterialTheme.colorScheme.tertiary
                is Status.Ready -> MaterialTheme.colorScheme.primary
                is Status.Error -> MaterialTheme.colorScheme.error
            }
        )
    }
}

@Composable
private fun StateDisplay(state: ScannerViewModel.State) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when (state) {
                ScannerViewModel.State.Default -> "Default"
                ScannerViewModel.State.Loading -> "Loading..."
                ScannerViewModel.State.Start -> "Started"
                ScannerViewModel.State.Stop -> "Stopped"
                is ScannerViewModel.State.Error -> "Error: ${state.error.message}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = when (state) {
                ScannerViewModel.State.Default -> MaterialTheme.colorScheme.onSurfaceVariant
                ScannerViewModel.State.Loading -> MaterialTheme.colorScheme.tertiary
                ScannerViewModel.State.Start -> MaterialTheme.colorScheme.primary
                ScannerViewModel.State.Stop -> MaterialTheme.colorScheme.onSurfaceVariant
                is ScannerViewModel.State.Error -> MaterialTheme.colorScheme.error
            }
        )
    }
}
