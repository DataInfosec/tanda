package com.tanda.biometrics.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanda.biometrics.domain.model.Status
import com.tanda.biometrics.domain.usecase.ObserveStatusUsecase
import com.tanda.biometrics.domain.usecase.StartUsecase
import com.tanda.biometrics.domain.usecase.StopUsecase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ScannerViewModel(
    private val startUsecase: StartUsecase,
    private val stopUsecase: StopUsecase,
    private val observeStatusUsecase: ObserveStatusUsecase,
) : ViewModel() {

    private val _status = MutableStateFlow<Status>(Status.Default)

    private val _state = MutableStateFlow<State>(State.Default)

    val status: StateFlow<Status> = _status.asStateFlow()

    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeStatusUsecase().collectLatest {
                _status.tryEmit(it)
            }
        }
    }

    fun start() {
        try {
            _state.tryEmit(State.Loading)
            startUsecase()
            _state.tryEmit(State.Start)
        } catch (error: Throwable) {
            _state.tryEmit(State.Error(error))
        }
    }

    fun stop() {
        try {
            _state.tryEmit(State.Loading)
            stopUsecase()
            _state.tryEmit(State.Stop)
        } catch (error: Throwable) {
            _state.tryEmit(State.Error(error))
        }
    }

    sealed interface State {
        object Default : State
        object Loading : State
        object Start : State
        object Stop : State
        data class Error(val error: Throwable) : State
    }
}
