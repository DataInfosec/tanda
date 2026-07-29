package com.tanda.attendance.ui.checkin

import androidx.lifecycle.ViewModel
import com.tanda.biometrics.domain.model.Image
import com.tanda.biometrics.domain.usecase.IdentificationUsecase
import com.tanda.core.common.concurrent.Dispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CheckinViewModel(
    private val dispatcher: Dispatcher,
    private val usecase: IdentificationUsecase
) : ViewModel() {
    private val _state = MutableStateFlow<State>(State.Default)

    val state: StateFlow<State> = _state.asStateFlow()

    operator fun invoke(id: String, image: Image) {}

    sealed interface State {
        data object Default : State
        data object Loading : State
        data class Success(val session: String) : State
        data class Error(val error: Throwable) : State
    }
}
