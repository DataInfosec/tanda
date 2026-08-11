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
import kotlinx.coroutines.launch

class FingerprintViewModel(
    private val dispatcher: Dispatcher,
    private val stateUsecase: ObserveStateUsecase,
    private val modeUsecase: ObserveModeUsecase,
    private val permissionUsecase: PermissionUsecase,
    private val permissionRequestUsecase: PermissionRequestUsecase,
    private val captureUsecase: CaptureUsecase
) : ViewModel() {
    private val _mode = MutableStateFlow<Mode>(Mode.Default)

    private val _status = MutableStateFlow<Status>(Status.Default)

    private val _state = MutableStateFlow<State>(State.Default)

    val mode: StateFlow<Mode> = _mode.asStateFlow()

    val status: StateFlow<Status> = _status.asStateFlow()


    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            stateUsecase().collectLatest {
                if (it is Snapshot.Capture) {
                    _status.tryEmit(Status.Capture(it.image))
                } else {
                    _status.tryEmit(Status.Default)
                }
            }
        }
        viewModelScope.launch {
            modeUsecase().collectLatest { _mode.tryEmit(it) }
        }
    }

    operator fun invoke(id: Int, index: Int) {
        viewModelScope.launch(dispatcher.io) {
            try {
                _state.tryEmit(State.Loading)
                captureUsecase(
                    CaptureUsecase.Argument(
                        finger = Finger.FLAT_SINGLE_FINGER,
                        index = index,
                        option = Option.IGNORE_FINGER_COUNT
                    )
                )
                _state.tryEmit(State.Initialized)
            } catch (error: Throwable) {
                _state.tryEmit(State.Error(error))
            }
        }
    }

    fun requirePermission(id: Int) {
        viewModelScope.launch {
            try {
                if (!permissionUsecase(id)) {
                    permissionRequestUsecase(id)
                }
            } catch (error: Throwable) {
                _state.tryEmit(State.Error(error))
            }
        }
    }

    sealed interface Status {
        data object Default : Status
        data class Capture(val image: Image) : Status
    }

    sealed interface State {
        data object Default : State
        data object Loading : State
        data object Initialized : State
        data class Error(val error: Throwable) : State
    }
}
