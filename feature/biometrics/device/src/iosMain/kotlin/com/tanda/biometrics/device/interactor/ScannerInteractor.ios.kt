package com.tanda.biometrics.device.interactor

import com.tanda.biometrics.domain.model.Option
import com.tanda.biometrics.domain.model.Posture
import com.tanda.biometrics.domain.model.Event
import com.tanda.biometrics.domain.model.State
import com.tanda.biometrics.domain.model.Status
import kotlinx.coroutines.flow.Flow

actual class ScannerInteractor {
    actual val state: Flow<State> get() = TODO("Not yet implemented")

    actual val status: Flow<Status> get() = TODO("Not yet implemented")

    actual val event: Flow<Event> get() = TODO("Not yet implemented")

    actual fun start() {}

    actual fun requestPermission(id: Int) {}

    actual fun capture(posture: Posture, index: Int, option: Option) {}

    actual fun stop() {}
}
