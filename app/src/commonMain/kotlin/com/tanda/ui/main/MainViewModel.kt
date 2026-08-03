package com.tanda.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanda.account.domain.usecase.ObserveTokenUsecase
import com.tanda.account.domain.usecase.TokenUsecase
import com.tanda.core.common.concurrent.Dispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val dispatcher: Dispatcher,
    private val tokenUsecase: TokenUsecase,
    private val observeTokenUsecase: ObserveTokenUsecase
) : ViewModel() {
    private val _state = MutableStateFlow<State>(State.Default)

    val state: StateFlow<State> = _state.asStateFlow()

    operator fun invoke() {
        viewModelScope.launch(dispatcher.io) {
            try {
                _state.tryEmit(State.Loading)
                _state.tryEmit(State.Success(
                    authenticated = !tokenUsecase().isNullOrBlank()
                ))
                observeTokenUsecase().collect { token ->
                    _state.tryEmit(State.Success(
                        authenticated = !token.isNullOrBlank()
                    ))
                }
            } catch (error: Throwable) {
                _state.tryEmit(State.Error(error))
            }
        }
    }

    sealed interface State {
        data object Default : State
        data object Loading : State
        data class Success(val authenticated: Boolean) : State
        data class Error(val error: Throwable) : State
    }
}
