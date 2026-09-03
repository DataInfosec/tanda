package com.tanda.biometrics.ui.subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanda.biometrics.domain.model.Subject
import com.tanda.biometrics.domain.usecase.SubjectUsecase
import com.tanda.core.common.concurrent.Dispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SubjectViewModel(
    private val dispatcher: Dispatcher,
    private val usecase: SubjectUsecase
) : ViewModel() {
    private val _state = MutableStateFlow<State>(State.Default)

    val state: StateFlow<State> = _state.asStateFlow()

    fun readSubject(
        reference: String,
        expectedSubjectType: String
    ) {
        viewModelScope.launch(dispatcher.io) {
            try {
                _state.value = State.Loading
                val subject = usecase(reference)
                if (!subject.type.equals(expectedSubjectType, ignoreCase = true)) {
                    error("Expected $expectedSubjectType record but found ${subject.type}")
                }
                _state.value = State.Success(subject)
            } catch (error: Throwable) {
                _state.value = State.Error(error)
            }
        }
    }

    fun reset() {
        _state.value = State.Default
    }

    sealed interface State {
        data object Default : State
        data object Loading : State
        data class Success(val subject: Subject) : State
        data class Error(val error: Throwable) : State
    }
}
