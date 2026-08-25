package com.tanda.biometrics.domain.session

import com.tanda.biometrics.domain.exception.DeviceNotFoundException
import com.tanda.biometrics.domain.model.ScannerSessionState
import com.tanda.biometrics.domain.model.Status
import com.tanda.biometrics.domain.usecase.ObserveStatusUsecase
import com.tanda.biometrics.domain.usecase.StartUsecase
import com.tanda.biometrics.domain.usecase.StopUsecase
import com.tanda.core.common.concurrent.Dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

@Single
class ScannerSessionManager(
    dispatcher: Dispatcher,
    private val startUsecase: StartUsecase,
    private val stopUsecase: StopUsecase,
    observeStatusUsecase: ObserveStatusUsecase,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher.io)
    private val _state = MutableStateFlow<ScannerSessionState>(ScannerSessionState.Stopped)
    private val active = MutableStateFlow(false)

    val state: StateFlow<ScannerSessionState> = _state.asStateFlow()

    init {
        scope.launch {
            observeStatusUsecase().collectLatest { status ->
                _state.emit(status.toSessionState(active.value))
            }
        }
    }

    fun start() {
        if (active.value) return
        active.value = true
        _state.tryEmit(ScannerSessionState.Starting)
        try {
            startUsecase()
        } catch (error: Throwable) {
            active.value = false
            _state.tryEmit(ScannerSessionState.Error(error))
        }
    }

    fun stop() {
        // Lifecycle teardown must always reach the device layer. Its stop operation is
        // idempotent and remains the source of truth for whether a native handle is open.
        active.value = false
        try {
            stopUsecase()
        } catch (error: Throwable) {
            _state.tryEmit(ScannerSessionState.Error(error))
        } finally {
            _state.tryEmit(ScannerSessionState.Stopped)
        }
    }

    fun retry() {
        stop()
        start()
    }

    private fun Status.toSessionState(active: Boolean): ScannerSessionState {
        return when (this) {
            Status.Default -> if (active) ScannerSessionState.Starting else ScannerSessionState.Stopped
            is Status.Attached -> ScannerSessionState.Starting
            is Status.Initialize -> ScannerSessionState.Initializing(progress)
            is Status.Ready -> ScannerSessionState.Ready(id, index)
            is Status.Detached -> ScannerSessionState.Error(DeviceNotFoundException())
            is Status.Error -> ScannerSessionState.Error(error)
        }
    }
}
