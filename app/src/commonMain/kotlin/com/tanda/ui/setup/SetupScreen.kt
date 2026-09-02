package com.tanda.ui.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanda.ui.setup.SetupEvent.Companion.LocalSetupEvent
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterIsInstance
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.scope.ScopeID
import tanda.app.generated.resources.Res
import tanda.app.generated.resources.error_default_message
import kotlin.time.Duration.Companion.milliseconds

@Composable
@OptIn(FlowPreview::class)
fun SetupScreen(scope: ScopeID) {
    val localEvent = LocalSetupEvent.current
    val current = getKoin().getScope(scope)
    val component = remember { Setup.Builder(current).build() }
    val viewModel: SetupViewModel = koinViewModel(scope = component)
    val state = viewModel.state.collectAsStateWithLifecycle()
    val errorMessage = stringResource(Res.string.error_default_message)
    val isLoading = remember {
        derivedStateOf { state.value is SetupViewModel.State.Loading }
    }
    val error = remember {
        derivedStateOf {
            (state.value as? SetupViewModel.State.Error)?.error?.let {
                it.message ?: errorMessage
            }
        }
    }

    SetupPage(
        isLoading = isLoading,
        error = error,
        onDismissClick = { localEvent(SetupEvent.Event.Dismiss) },
        onContinueClick = viewModel::invoke,
    )

    LaunchedEffect(viewModel) {
        viewModel.state
            .filterIsInstance<SetupViewModel.State.Success>()
            .debounce(300.milliseconds)
            .collect {
                localEvent(SetupEvent.Event.Complete)
            }
    }
}
