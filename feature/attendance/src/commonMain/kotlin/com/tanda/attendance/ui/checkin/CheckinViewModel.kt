package com.tanda.attendance.ui.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanda.biometrics.domain.model.Capture
import com.tanda.biometrics.domain.model.Image
import com.tanda.biometrics.domain.usecase.IdentificationUsecase
import com.tanda.core.common.concurrent.Dispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CheckinViewModel(
    private val dispatcher: Dispatcher,
    private val usecase: IdentificationUsecase
) : ViewModel() {
    private val _state = MutableStateFlow<State>(State.Default)

    val state: StateFlow<State> = _state.asStateFlow()

    operator fun invoke(image: Image) {
        viewModelScope.launch(dispatcher.io) {
            _state.tryEmit(State.Loading)
            try {
                _state.tryEmit(State.Success(usecase.invoke(image)))
            } catch (error: Throwable) {
                _state.tryEmit(State.Error(error))
            }
        }
    }

    fun reset() {
        _state.tryEmit(State.Default)
    }

    sealed interface State {
        data object Default : State
        data object Loading : State
        data class Success(val capture: Capture) : State
        data class Error(val error: Throwable) : State
    }
}
