package com.tanda.biometrics.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanda.biometrics.domain.model.Image
import com.tanda.biometrics.domain.model.Mode
import com.tanda.biometrics.domain.model.Option
import com.tanda.biometrics.domain.model.Posture
import com.tanda.biometrics.domain.usecase.CaptureUsecase
import com.tanda.biometrics.domain.usecase.ObserveModeUsecase
import com.tanda.biometrics.domain.usecase.ObserveStateUsecase
import com.tanda.biometrics.domain.usecase.PermissionRequestUsecase
import com.tanda.biometrics.domain.usecase.PermissionUsecase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ScannerViewModel(
    private val stateUsecase: ObserveStateUsecase,
    private val modeUsecase: ObserveModeUsecase,
    private val permissionUsecase: PermissionUsecase,
    private val permissionRequestUsecase: PermissionRequestUsecase,
    private val captureUsecase: CaptureUsecase
) : ViewModel() {
    private val _status = MutableStateFlow<Status>(Status.Default(Mode.Default))

    private val _state = MutableStateFlow<State>(State.Default)

    val status: StateFlow<Status> = _status.asStateFlow()

    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(stateUsecase(), modeUsecase()) { state, mode ->
                if (state is com.tanda.biometrics.domain.model.State.Capture) {
                    Status.Capture(mode, state.image)
                } else {
                    Status.Default(mode)
                }
            }.collectLatest { _status.tryEmit(it) }
        }
    }

    operator fun invoke(id: Int, index: Int) {
        viewModelScope.launch {
            _state.tryEmit(State.Loading)
            if (!permissionUsecase(id)) {
                permissionRequestUsecase(id)
            }
            captureUsecase(
                CaptureUsecase.Argument(
                    posture = Posture.FLAT_SINGLE_FINGER,
                    index = index,
                    option = Option.AUTO_CAPTURE
                )
            )
            _state.tryEmit(State.Captured)
        }
    }

    sealed class Status(val mode: Mode) {
        data class Default(private val _mode: Mode) : Status(_mode)
        data class Capture(
            private val _mode: Mode,
            private val image: Image
        ) : Status(_mode)
    }

    sealed interface State {
        data object Default : State
        data object Loading : State
        data object Captured : State
    }
}
