package com.tanda.account.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanda.account.domain.usecase.LoginUsecase
import com.tanda.core.common.concurrent.Dispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val dispatcher: Dispatcher,
    private val usecase: LoginUsecase
) : ViewModel() {
    private val _state = MutableStateFlow<State>(State.Default)

    val state: StateFlow<State> = _state.asStateFlow()

    operator fun invoke(login: String, password: String) {
        viewModelScope.launch(dispatcher.io) {
            _state.value = State.Loading
            try {
                val token = usecase(
                    LoginUsecase.Argument(
                        login = login,
                        password = password
                    )
                )
                _state.value = State.Success(token)
            } catch (e: Throwable) {
                _state.value = State.Error(e)
            }
        }
    }

    sealed interface State {
        data object Default : State
        data object Loading : State
        data class Success(val token: String) : State
        data class Error(val error: Throwable) : State
    }
}
