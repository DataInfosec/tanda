package com.tanda.attendance.ui.enrollment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanda.biometrics.domain.model.Image
import com.tanda.biometrics.domain.usecase.EnrollmentUsecase
import com.tanda.core.common.concurrent.Dispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EnrollmentViewModel(
    private val dispatcher: Dispatcher,
    private val usecase: EnrollmentUsecase
) : ViewModel() {
    private val _state = MutableStateFlow<State>(State.Default)

    val state: StateFlow<State> = _state.asStateFlow()

    operator fun invoke(id: String, image: Image) {
        viewModelScope.launch(dispatcher.io) {
            try {
                _state.tryEmit(State.Loading)
                val session = usecase(
                    EnrollmentUsecase.Argument(
                        id = id,
                        images = listOf(image),
                    )
                )
                _state.tryEmit(State.Success(session))
            } catch (error: Throwable) {
                _state.tryEmit(State.Error(error))
            }
        }
    }

    sealed interface State {
        data object Default : State
        data object Loading : State
        data class Success(val session: String) : State
        data class Error(val error: Throwable) : State
    }
}
