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

    operator fun invoke(reference: String) {
        viewModelScope.launch(dispatcher.io) {
            try {
                _state.value = State.Loading
                _state.value = State.Success(usecase(reference))
            } catch (error: Throwable) {
                _state.value = State.Error(error)
            }
        }
    }

    sealed interface State {
        data object Default : State
        data object Loading : State
        data class Success(val subject: Subject) : State
        data class Error(val error: Throwable) : State
    }
}
