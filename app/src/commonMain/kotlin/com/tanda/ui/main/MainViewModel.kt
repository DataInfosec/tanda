package com.tanda.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanda.account.domain.usecase.ObserveTokenUsecase
import com.tanda.account.domain.usecase.TokenUsecase
import com.tanda.biometrics.domain.model.ScannerSessionState
import com.tanda.biometrics.domain.session.ScannerSessionManager
import com.tanda.core.common.concurrent.Dispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val dispatcher: Dispatcher,
    private val tokenUsecase: TokenUsecase,
    private val observeTokenUsecase: ObserveTokenUsecase,
    private val scannerSessionManager: ScannerSessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow<State>(State.Default)
    private val _effect = MutableSharedFlow<Effect>(
        extraBufferCapacity = 1
    )

    val state: StateFlow<State> = _state.asStateFlow()
    val effect: SharedFlow<Effect> = _effect.asSharedFlow()
    val scannerState: StateFlow<ScannerSessionState> = scannerSessionManager.state

    fun retryScanner() {
        scannerSessionManager.retry()
    }

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
        viewModelScope.launch {
            observeTokenUsecase.expiration().collect {
                _effect.tryEmit(Effect.SessionExpired)
            }
        }
    }

    sealed interface State {
        data object Default : State
        data object Loading : State
        data class Success(val authenticated: Boolean) : State
        data class Error(val error: Throwable) : State
    }

    sealed interface Effect {
        data object SessionExpired : Effect
    }
}
