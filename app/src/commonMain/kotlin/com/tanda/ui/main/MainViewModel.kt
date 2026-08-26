package com.tanda.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanda.biometrics.domain.usecase.StartUsecase
import com.tanda.biometrics.domain.usecase.StopUsecase
import com.tanda.core.common.concurrent.Dispatcher
import com.tanda.core.common.interactor.LocaleInteractor
import com.tanda.core.common.model.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainViewModel(
    dispatcher: Dispatcher,
    private val usecase: StartUsecase,
    private val stopUsecase: StopUsecase,
    private val interactor: LocaleInteractor,
) : ViewModel() {
    private val _state = MutableStateFlow<State>(State.Default)

    private val _locale = MutableStateFlow(interactor.current())

    val state: StateFlow<State> = _state.asStateFlow()

    val locale: StateFlow<Locale> = _locale.asStateFlow()

    init {
        viewModelScope.launch(dispatcher.io) {
            interactor.observe()
                .collectLatest { _locale.value = it }
        }
    }

    operator fun invoke(enable: Boolean = true) {
        try {
            _state.value = State.Loading
            if (enable) {
                usecase()
            } else {
                stopUsecase()
            }
            _state.value = State.Success
        } catch (error: Throwable) {
            _state.value = State.Error(error)
        }
    }

    sealed interface State {
        data object Default : State
        data object Loading : State
        data object Success : State
        data class Error(val error: Throwable) : State
    }
}
