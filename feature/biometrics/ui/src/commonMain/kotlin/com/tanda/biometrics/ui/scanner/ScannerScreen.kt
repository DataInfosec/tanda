package com.tanda.biometrics.ui.scanner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanda.biometrics.domain.model.Status
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.core.ui.design.DesignStreamState
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.scope.ScopeID

@Composable
fun ScannerScreen(
    scope: ScopeID,
    content: @Composable (ScannerViewModel, State<DesignStreamState<Status.Ready>>) -> Unit
) {
    val factory = getKoin().getScope(scope).get<UiComponentProvider.Factory>()
    val component = remember { factory.builder(Scanner.Builder::class).build() }
    val viewModel: ScannerViewModel = koinViewModel(scope = component)
    val status = viewModel.status.collectAsStateWithLifecycle()
    val state = viewModel.state.collectAsStateWithLifecycle()
    val updatedContent by rememberUpdatedState(content)
    val derivedState = remember { derivedStateOf {
        /*val currentState = state.value
        if (currentState is ScannerViewModel.State.Error) {
            return@derivedStateOf DesignStreamState.Error(currentState.error)
        }*/
        when (val current = status.value) {
            is Status.Ready -> DesignStreamState.Success(current)
            is Status.Detached -> DesignStreamState.Default
            is Status.Error -> DesignStreamState.Error(current.error)
            else -> DesignStreamState.Loading
        }
    } }
    updatedContent(viewModel, derivedState)
    LaunchedEffect(Unit) { viewModel.start() }
}
