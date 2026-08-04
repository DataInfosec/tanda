package com.tanda.account.ui.login

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanda.core.ui.component.UiComponentProvider
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.scope.ScopeID

@Composable
fun LoginScreen(scope: ScopeID) {
    val factory = getKoin().getScope(scope).get<UiComponentProvider.Factory>()
    val component = remember { factory.builder(Login.Builder::class).build() }
    val viewModel: LoginViewModel = koinViewModel(scope = component)
    val state = viewModel.state.collectAsStateWithLifecycle()
    val email = remember { TextFieldState() }
    val password = remember { TextFieldState() }
    val isLoading = remember { derivedStateOf { state.value is LoginViewModel.State.Loading } }
    val error = remember { derivedStateOf {
        (state.value as? LoginViewModel.State.Error)?.error?.let {
            it.message ?: "An error occurred"
        }
    } }
    LoginPage(
        email = email,
        password = password,
        isLoading = isLoading,
        error = error
    ) { viewModel(email.text.toString(), password.text.toString()) }
}
