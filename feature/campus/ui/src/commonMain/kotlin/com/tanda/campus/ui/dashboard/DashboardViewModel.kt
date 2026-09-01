package com.tanda.campus.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanda.campus.domain.usecase.ObserveProfileNameUsecase
import com.tanda.campus.domain.usecase.ProfileNameUsecase
import com.tanda.core.common.concurrent.Dispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val dispatcher: Dispatcher,
    private val profileNameUsecase: ProfileNameUsecase,
    private val observeProfileNameUsecase: ObserveProfileNameUsecase,
) : ViewModel() {
    private val _state = MutableStateFlow<State>(State.Default)

    val state: StateFlow<State> = _state.asStateFlow()

    operator fun invoke() {
        viewModelScope.launch(dispatcher.io) {
            try {
                _state.tryEmit(State.Success(profileNameUsecase()))
                observeProfileNameUsecase().collect { name ->
                    _state.tryEmit(State.Success(name))
                }
            } catch (error: Throwable) {
                _state.tryEmit(State.Error(error))
            }
        }
    }

    sealed interface State {
        data object Default : State
        data class Success(val name: String?) : State
        data class Error(val error: Throwable) : State
    }
}
