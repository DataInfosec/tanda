package com.tanda.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanda.account.domain.usecase.ObserveTokenUsecase
import com.tanda.core.common.concurrent.Dispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    dispatcher: Dispatcher,
    private val observeTokenUsecase: ObserveTokenUsecase
) : ViewModel() {
    private val _state = MutableStateFlow<State>(State.Default)

    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch(dispatcher.io) {
            observeTokenUsecase().collect { token ->
                _state.tryEmit(State.Success(
                    authenticated = !token.isNullOrBlank()
                ))
            }
        }
    }

    sealed interface State {
        data object Default : State
        data class Success(val authenticated: Boolean) : State
    }
}
