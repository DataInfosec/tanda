package com.tanda.biometrics.ui.fingerprint

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanda.biometrics.domain.model.Image
import com.tanda.biometrics.domain.model.Mode
import com.tanda.biometrics.domain.model.Option
import com.tanda.biometrics.domain.model.Finger
import com.tanda.biometrics.domain.model.Snapshot
import com.tanda.biometrics.domain.usecase.CaptureUsecase
import com.tanda.biometrics.domain.usecase.ObserveModeUsecase
import com.tanda.biometrics.domain.usecase.ObserveStateUsecase
import com.tanda.biometrics.domain.usecase.PermissionRequestUsecase
import com.tanda.biometrics.domain.usecase.PermissionUsecase
import com.tanda.core.common.concurrent.Dispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class FingerprintViewModel(
    private val dispatcher: Dispatcher,
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
                if (state is Snapshot.Capture) {
                    Status.Capture(mode, state.image)
                } else {
                    Status.Default(mode)
                }
            }.collectLatest { _status.tryEmit(it) }
        }
    }

    operator fun invoke(id: Int) {
        viewModelScope.launch {
            if (!permissionUsecase(id)) {
                permissionRequestUsecase(id)
            }
        }
    }

    operator fun invoke(id: Int, index: Int) {
        viewModelScope.launch(dispatcher.io) {
            _state.tryEmit(State.Loading)
            captureUsecase(
                CaptureUsecase.Argument(
                    finger = Finger.FLAT_SINGLE_FINGER,
                    index = index,
                    option = Option.IGNORE_FINGER_COUNT
                )
            )
            _state.tryEmit(State.Initialized)
        }
    }

    sealed class Status(val mode: Mode) {
        data class Default(private val _mode: Mode) : Status(_mode)
        data class Capture(
            private val _mode: Mode,
            val image: Image
        ) : Status(_mode)
    }

    sealed interface State {
        data object Default : State
        data object Loading : State
        data object Initialized : State
    }
}
