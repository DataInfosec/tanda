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

    private val _deviceId = MutableStateFlow<Int?>(null)

    private val _state = MutableStateFlow<State>(State.Default)

    val status: StateFlow<Status> = _status.asStateFlow()

    val deviceId: StateFlow<Int?> = _deviceId.asStateFlow()

    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeStatusUsecase().collectLatest {
                _status.tryEmit(it)
                _deviceId.tryEmit(
                    when (it) {
                        is Status.Attached -> it.id
                        is Status.Initialize -> it.id
                        is Status.Ready -> it.id
                        else -> null
                    }
                )
            }
        }
    }

    fun start() {
        try {
            _state.tryEmit(State.Loading)
            startUsecase()
            _state.tryEmit(State.Success(true))
        } catch (error: Throwable) {
            _state.tryEmit(State.Error(error))
        }
    }

    fun stop() {
        try {
            _state.tryEmit(State.Loading)
            stopUsecase()
            _state.tryEmit(State.Success(false))
        } catch (error: Throwable) {
            _state.tryEmit(State.Error(error))
        }
    }

    sealed interface State {
        object Default : State
        object Loading : State
        data class Success(val active: Boolean) : State
        data class Error(val error: Throwable) : State
    }
}
