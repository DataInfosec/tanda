package com.tanda.attendance.ui.student

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanda.attendance.domain.model.AttendanceOption
import com.tanda.attendance.domain.usecase.ObserveStudentAttendancePointsUsecase
import com.tanda.attendance.domain.usecase.RefreshStudentAttendancePointsUsecase
import com.tanda.core.common.concurrent.Dispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StudentAttendanceViewModel(
    private val dispatcher: Dispatcher,
    private val observePointsUsecase: ObserveStudentAttendancePointsUsecase,
    private val refreshPointsUsecase: RefreshStudentAttendancePointsUsecase,
) : ViewModel() {
    private val _state = MutableStateFlow(State())
    private var started = false

    val state: StateFlow<State> = _state.asStateFlow()

    fun start() {
        if (started) return
        started = true
        viewModelScope.launch(dispatcher.io) {
            observePointsUsecase().collect { options ->
                _state.update {
                    it.copy(
                        options = options,
                        isLoading = if (options.isNotEmpty()) false else it.isLoading,
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(dispatcher.io) {
            _state.update {
                it.copy(
                    isRefreshing = true,
                    errorMessage = null,
                    usingCachedPoints = false,
                )
            }
            try {
                refreshPointsUsecase()
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                    )
                }
            } catch (error: Throwable) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = error.message ?: "Unable to load attendance points",
                        usingCachedPoints = it.options.isNotEmpty(),
                    )
                }
            }
        }
    }

    data class State(
        val options: List<AttendanceOption> = emptyList(),
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val errorMessage: String? = null,
        val usingCachedPoints: Boolean = false,
    )
}
