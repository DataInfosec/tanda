package com.tanda.biometrics.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanda.biometrics.domain.model.Status
import com.tanda.biometrics.domain.usecase.ObserveStatusUsecase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ScannerViewModel(
    private val observeStatusUsecase: ObserveStatusUsecase,
) : ViewModel() {

    private val _state = MutableStateFlow<Status>(Status.Default)

    val state: StateFlow<Status> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeStatusUsecase().collectLatest {
                _state.tryEmit(it)
            }
        }
    }
}
