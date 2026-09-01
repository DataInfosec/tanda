package com.tanda.biometrics.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanda.biometrics.domain.exception.FingerprintException
import com.tanda.biometrics.domain.model.Image
import com.tanda.biometrics.domain.model.Subject
import com.tanda.biometrics.domain.usecase.EnrollmentUsecase
import com.tanda.biometrics.domain.usecase.IdentificationUsecase
import com.tanda.biometrics.domain.usecase.ReadSubjectUsecase
import com.tanda.biometrics.domain.usecase.SynchronizeUsecase
import com.tanda.core.common.concurrent.Dispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SubjectBiometricViewModel(
    private val dispatcher: Dispatcher,
    private val readSubjectUsecase: ReadSubjectUsecase,
    private val identificationUsecase: IdentificationUsecase,
    private val enrollmentUsecase: EnrollmentUsecase,
    private val syncUsecase: SynchronizeUsecase
) : ViewModel() {
    private val _state = MutableStateFlow<State>(State.Default)
    private val _enrollmentState = MutableStateFlow<EnrollmentState>(EnrollmentState.Default)

    val state: StateFlow<State> = _state.asStateFlow()
    val enrollmentState: StateFlow<EnrollmentState> = _enrollmentState.asStateFlow()

    fun readSubject(
        externalReference: String,
        expectedSubjectType: String,
    ) {
        viewModelScope.launch(dispatcher.io) {
            _state.tryEmit(State.Loading)
            try {
                val subject = readSubjectUsecase(
                    ReadSubjectUsecase.Argument(
                        externalReference = externalReference
                    )
                )
                require(subject.subjectType.equals(expectedSubjectType, ignoreCase = true)) {
                    "This ID does not belong to a ${expectedSubjectType.lowercase()}"
                }
                require(subject.lifecycleStatus.equals(ACTIVE_STATUS, ignoreCase = true)) {
                    "This subject is not active"
                }
                _state.tryEmit(State.Success(subject))
            } catch (throwable: Throwable) {
                _state.tryEmit(
                    State.Error(
                        throwable.message ?: "Unable to fetch subject detail"
                    )
                )
            }
        }
    }

    fun enrollSubjectFingerprint(subjectId: String, image: Image) {
        viewModelScope.launch(dispatcher.io) {
            _enrollmentState.tryEmit(EnrollmentState.Loading)
            try {
                val match = try {
                    identificationUsecase(image)
                } catch (error: FingerprintException) {
                    if (shouldProceedToEnrollment(error.message)) {
                        null
                    } else {
                        _enrollmentState.tryEmit(
                            EnrollmentState.Error(
                                identificationRetryMessage(error.message)
                            )
                        )
                        return@launch
                    }
                }

                if (match != null) {
                    val message = if (match.id == subjectId) {
                        "Fingerprint already exists for this subject"
                    } else {
                        "Fingerprint is already enrolled for another subject"
                    }
                    _enrollmentState.tryEmit(
                        EnrollmentState.Error(message)
                    )
                    return@launch
                }

                enrollmentUsecase(
                    EnrollmentUsecase.Argument(
                        id = subjectId,
                        images = listOf(image)
                    )
                )
                syncUsecase()
                _enrollmentState.tryEmit(EnrollmentState.Success)
            } catch (throwable: Throwable) {
                _enrollmentState.tryEmit(
                    EnrollmentState.Error(
                        throwable.message ?: "Unable to enroll fingerprint"
                    )
                )
            }
        }
    }

    fun reset() {
        _state.tryEmit(State.Default)
    }

    fun resetEnrollment() {
        _enrollmentState.tryEmit(EnrollmentState.Default)
    }

    sealed interface State {
        data object Default : State
        data object Loading : State
        data class Success(val subject: Subject) : State
        data class Error(val message: String) : State
    }

    sealed interface EnrollmentState {
        data object Default : EnrollmentState
        data object Loading : EnrollmentState
        data object Success : EnrollmentState
        data class Error(val message: String) : EnrollmentState
    }

    private fun identificationRetryMessage(reason: String?): String {
        return when (reason) {
            LOW_QUALITY_REASON -> "Fingerprint quality is too low. Please scan again"
            WEAK_SCORE_REASON -> "Fingerprint could not be verified. Please scan again"
            AMBIGUOUS_REASON -> "Fingerprint matched multiple subjects. Please scan again"
            else -> "Fingerprint could not be verified. Please scan again"
        }
    }

    private companion object {
        const val ACTIVE_STATUS = "active"
        const val LOW_QUALITY_REASON = "LOW_QUALITY"
        const val WEAK_SCORE_REASON = "WEAK_SCORE"
        const val AMBIGUOUS_REASON = "AMBIGUOUS"
    }
}

internal fun shouldProceedToEnrollment(reason: String?): Boolean {
    return reason == "NO_CANDIDATES" || reason == "WEAK_SCORE"
}
