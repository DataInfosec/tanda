package com.tanda.campus.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanda.account.domain.model.Account
import com.tanda.account.domain.usecase.account.AccountUsecase
import com.tanda.core.common.concurrent.Dispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(
    dispatcher: Dispatcher,
    private val usecase: AccountUsecase
) : ViewModel() {
    private val _state = MutableStateFlow<State>(State.Default)

    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch(dispatcher.io) {
            try {
                _state.value = State.Loading
                _state.value = State.Success(usecase())
            } catch (error: Throwable) {
                _state.value = State.Error(error)
            }
        }
    }

    sealed interface State {
        data object Default : State
        data object Loading : State
        data class Success(val account: Account) : State
        data class Error(val error: Throwable) : State
    }
}
