package com.tanda.attendance.ui.student

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanda.attendance.domain.model.AttendanceAction
import com.tanda.attendance.domain.model.AttendanceRecord
import com.tanda.attendance.domain.usecase.RecordStudentAttendanceUsecase
import com.tanda.biometrics.domain.exception.FingerprintException
import com.tanda.biometrics.domain.model.Image
import com.tanda.core.common.concurrent.Dispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StudentAttendanceCaptureViewModel(
    private val dispatcher: Dispatcher,
    private val recordAttendanceUsecase: RecordStudentAttendanceUsecase,
) : ViewModel() {
    private val _state = MutableStateFlow<State>(State.Default)

    val state: StateFlow<State> = _state.asStateFlow()

    fun record(pointId: String, action: AttendanceAction, image: Image) {
        if (_state.value is State.Processing) return
        viewModelScope.launch(dispatcher.io) {
            _state.emit(State.Processing)
            try {
                val record = recordAttendanceUsecase(
                    RecordStudentAttendanceUsecase.Argument(
                        pointId = pointId,
                        action = action,
                        image = image,
                    )
                )
                _state.emit(State.Success(record))
            } catch (error: Throwable) {
                val message = when (error) {
                    is FingerprintException -> {
                        "score: ${error.score} \n threshold: ${error.threshold} \n reason: ${error.message}"
                    }
                    else -> error.message ?: "Unable to record student attendance"
                }
                _state.emit(State.Error(message))
            }
        }
    }

    fun reset() {
        _state.value = State.Default
    }

    sealed interface State {
        data object Default : State
        data object Processing : State
        data class Success(val record: AttendanceRecord) : State
        data class Error(val message: String) : State
    }
}
