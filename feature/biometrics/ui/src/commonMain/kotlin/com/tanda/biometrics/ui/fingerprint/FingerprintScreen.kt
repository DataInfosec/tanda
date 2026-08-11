package com.tanda.biometrics.ui.fingerprint

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.core.ui.design.DesignStreamState
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.scope.ScopeID

@Composable
fun FingerprintScreen(
    scope: ScopeID,
    deviceId: Int,
    content: @Composable (FingerprintViewModel, State<DesignStreamState<Unit>>) -> Unit
) {
    val factory = getKoin().getScope(scope).get<UiComponentProvider.Factory>()
    val component = remember { factory.builder(Fingerprint.Builder::class).build() }
    val viewModel: FingerprintViewModel = koinViewModel(scope = component)
    val state = viewModel.state.collectAsStateWithLifecycle()
    val updatedContent by rememberUpdatedState(content)
    val derivedState = remember { derivedStateOf {
        when(state.value) {
            is FingerprintViewModel.State.Default -> DesignStreamState.Default
            is FingerprintViewModel.State.Loading -> DesignStreamState.Loading
            is FingerprintViewModel.State.Initialized -> DesignStreamState.Success(Unit)
            is FingerprintViewModel.State.Error -> {
                DesignStreamState.Error((state.value as FingerprintViewModel.State.Error).error)
            }
        }
    } }
    updatedContent(viewModel, derivedState)
    LaunchedEffect(deviceId) { viewModel.requirePermission(deviceId) }
}
