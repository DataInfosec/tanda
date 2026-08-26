package com.tanda.account.ui.login

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanda.account.ui.login.LoginEvent.Companion.LocalLoginEvent
import com.tanda.core.ui.component.UiComponentProvider
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.scope.ScopeID
import tanda.feature.account.ui.generated.resources.Res
import tanda.feature.account.ui.generated.resources.error_default_message
import kotlin.time.Duration.Companion.milliseconds

@Composable
@OptIn(FlowPreview::class)
fun LoginScreen(scope: ScopeID) {
    val localEvent = LocalLoginEvent.current
    val factory = getKoin().getScope(scope).get<UiComponentProvider.Factory>()
    val component = remember { factory.builder(Login.Builder::class).build() }
    val viewModel: LoginViewModel = koinViewModel(scope = component)
    val state = viewModel.state.collectAsStateWithLifecycle()
    val email = remember { TextFieldState() }
    val password = remember { TextFieldState() }
    val isLoading = remember { derivedStateOf { state.value is LoginViewModel.State.Loading } }
    val isSuccess = remember { derivedStateOf { state.value is LoginViewModel.State.Success } }
    val errorMessage = stringResource(Res.string.error_default_message)
    val error = remember { derivedStateOf {
        (state.value as? LoginViewModel.State.Error)?.error?.let {
            it.message ?: errorMessage
        }
    } }
    val focusRequester = remember { FocusRequester() }
    val animatable = remember { Animatable(0f) }
    val keyboardController = LocalSoftwareKeyboardController.current
    LoginPage(
        email = email,
        password = password,
        isLoading = isLoading,
        focusRequester = focusRequester,
        error = error
    ) { viewModel(email.text.toString(), password.text.toString()) }
    LaunchedEffect(Unit) {
        snapshotFlow { isLoading.value to isSuccess.value }
            .debounce(300.milliseconds)
            .collect {
                if (!it.second) {
                    focusRequester.requestFocus()
                    animatable.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 1)
                    )
                    keyboardController?.show()
                } else if (it.second) {
                    localEvent(LoginEvent.Event.Home)
                }
            }
    }
}
