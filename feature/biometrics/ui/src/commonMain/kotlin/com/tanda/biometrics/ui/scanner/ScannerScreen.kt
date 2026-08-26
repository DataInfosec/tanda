package com.tanda.biometrics.ui.scanner

import androidx.compose.runtime.Composable
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
    content: @Composable (State<DesignStreamState<Int>>) -> Unit
) {
    val factory = getKoin().getScope(scope).get<UiComponentProvider.Factory>()
    val component = remember { factory.builder(Scanner.Builder::class).build() }
    val viewModel: ScannerViewModel = koinViewModel(scope = component)
    val state = viewModel.state.collectAsStateWithLifecycle()
    val updatedContent by rememberUpdatedState(content)
    val derivedState = remember { derivedStateOf {
        when(state.value) {
            is Status.Detached -> DesignStreamState.Default
            is Status.Initialize -> DesignStreamState.Success((state.value as Status.Initialize).id)
            is Status.Attached -> DesignStreamState.Success((state.value as Status.Attached).id)
            is Status.Error -> DesignStreamState.Error((state.value as Status.Error).error)
            else -> DesignStreamState.Loading
        }
    } }
    updatedContent(derivedState)
}
