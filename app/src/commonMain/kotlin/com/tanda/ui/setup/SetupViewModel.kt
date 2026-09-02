package com.tanda.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanda.account.domain.model.DeviceActivation
import com.tanda.account.domain.usecase.device.ActivateDeviceUsecase
import com.tanda.core.common.concurrent.Dispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SetupViewModel(
    private val dispatcher: Dispatcher,
    private val usecase: ActivateDeviceUsecase
): ViewModel() {
    private val _state = MutableStateFlow<State>(State.Default)

    val state: StateFlow<State> = _state.asStateFlow()

    operator fun invoke(activationCode: String) {
        viewModelScope.launch(dispatcher.io) {
            try {
                _state.value = State.Loading
                _state.value = State.Success(
                    usecase(ActivateDeviceUsecase.Argument(activationCode))
                )
            } catch (error: Throwable) {
                _state.value = State.Error(error)
            }
        }
    }

    sealed interface State {
        data object Default : State
        data object Loading : State
        data class Success(val activation: DeviceActivation) : State
        data class Error(val error: Throwable) : State
    }
}
