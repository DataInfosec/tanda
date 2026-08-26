package com.tanda.biometrics.device.interactor

import com.tanda.biometrics.domain.model.Option
import com.tanda.biometrics.domain.model.Finger
import com.tanda.biometrics.domain.model.Mode
import com.tanda.biometrics.domain.model.Snapshot
import com.tanda.biometrics.domain.model.Status
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

actual class ScannerInteractor {
    private val _state = MutableSharedFlow<Snapshot>(replay = REPLAY)

    private val _status = MutableSharedFlow<Status>(replay = REPLAY)

    private val _mode = MutableSharedFlow<Mode>(replay = REPLAY)

    actual val state: Flow<Snapshot> get() = _state

    actual val status: Flow<Status> get() = _status

    actual val mode: Flow<Mode> get() = _mode

    init {
        _state.tryEmit(Snapshot.Default)
        _status.tryEmit(Status.Default)
        _mode.tryEmit(Mode.Default)
    }

    actual fun start() {}

    actual fun observe(): Flow<Boolean?> { TODO("Not yet implemented") }

    actual fun isActive(): Boolean = false

    actual fun hasPermission(id: Int): Boolean { TODO("Not yet implemented") }

    actual fun requestPermission(id: Int) {}

    actual suspend fun capture(finger: Finger, index: Int, option: Option) {}

    actual fun count(): Int = 0

    actual fun stop() {}

    private companion object {
        const val REPLAY = 1
    }
}
