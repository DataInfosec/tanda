package com.tanda.attendance.ui.enrollment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanda.BuildConstants
import com.tanda.attendance.interactor.StartEnrollmentUsecase
import com.tanda.biometrics.domain.model.Image
import com.tanda.biometrics.domain.usecase.EnrollmentUsecase
import com.tanda.core.common.concurrent.Dispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class EnrollmentViewModel(
    private val dispatcher: Dispatcher,
    private val startEnrollment: StartEnrollmentUsecase,
    private val usecase: EnrollmentUsecase
) : ViewModel() {
    private val _state = MutableStateFlow<State>(State.Default)

    val state: StateFlow<State> = _state.asStateFlow()

    @OptIn(ExperimentalUuidApi::class)
    fun authorize(externalReference: String) {
        viewModelScope.launch(dispatcher.io) {
            try {
                _state.tryEmit(State.Authorizing)
                val start = startEnrollment(
                    externalReference = externalReference,
                    idempotencyKey = Uuid.random().toString(),
                )
                if (!start.captureRequired) {
                    _state.tryEmit(State.Success(start.subjectId))
                    return@launch
                }
                val authorization = requireNotNull(start.authorization) {
                    "enrollment authorization is required when capture is required"
                }
                require(authorization.subjectId == start.subjectId) {
                    "enrollment authorization subject does not match the server result"
                }
                require(authorization.deviceInstanceId == BuildConstants.DEVICE_ID) {
                    "enrollment authorization device does not match this app installation"
                }
                _state.tryEmit(
                    State.Authorized(
                        subjectId = start.subjectId,
                        enrollmentOperationId = authorization.enrollmentOperationId,
                    )
                )
            } catch (error: Throwable) {
                _state.tryEmit(State.Error(error))
            }
        }
    }

    fun enroll(image: Image) {
        val authorization = _state.value as? State.Authorized ?: return
        viewModelScope.launch(dispatcher.io) {
            try {
                _state.tryEmit(State.Loading)
                val session = usecase(
                    EnrollmentUsecase.Argument(
                        id = authorization.subjectId,
                        images = listOf(image),
                        batchId = authorization.enrollmentOperationId,
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
        data object Authorizing : State
        data class Authorized(
            val subjectId: String,
            val enrollmentOperationId: String,
        ) : State
        data object Loading : State
        data class Success(val session: String) : State
        data class Error(val error: Throwable) : State
    }
}
