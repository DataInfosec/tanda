package com.tanda.biometrics.device.interactor

import com.tanda.biometrics.domain.model.Option
import com.tanda.biometrics.domain.model.Posture
import com.tanda.biometrics.domain.model.Mode
import com.tanda.biometrics.domain.model.State
import com.tanda.biometrics.domain.model.Status
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

actual class ScannerInteractor {
    private val _state = MutableSharedFlow<State>(replay = REPLAY)

    private val _status = MutableSharedFlow<Status>(replay = REPLAY)

    private val _mode = MutableSharedFlow<Mode>(replay = REPLAY)

    actual val state: Flow<State> get() = _state

    actual val status: Flow<Status> get() = _status

    actual val mode: Flow<Mode> get() = _mode

    init {
        _state.tryEmit(State.Default)
        _status.tryEmit(Status.Default)
        _mode.tryEmit(Mode.Default)
    }

    actual fun start() {}

    actual fun hasPermission(id: Int): Boolean { TODO("Not yet implemented") }

    actual fun requestPermission(id: Int) {}

    actual suspend fun capture(posture: Posture, index: Int, option: Option) {}

    actual fun stop() {}

    private companion object {
        const val REPLAY = 1
    }
}
