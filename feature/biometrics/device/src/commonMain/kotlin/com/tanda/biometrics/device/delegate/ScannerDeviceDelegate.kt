package com.tanda.biometrics.device.delegate

import com.tanda.biometrics.data.device.ScannerDevice
import com.tanda.biometrics.device.interactor.ScannerInteractor
import com.tanda.biometrics.domain.model.Event
import com.tanda.biometrics.domain.model.Option
import com.tanda.biometrics.domain.model.Posture
import com.tanda.biometrics.domain.model.State
import com.tanda.biometrics.domain.model.Status
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Singleton

@Singleton
class ScannerDeviceDelegate(private val interactor: ScannerInteractor) : ScannerDevice {
    override val state: Flow<State> get() = interactor.state

    override val status: Flow<Status> get() = interactor.status

    override val event: Flow<Event> get() = interactor.event

    override fun start() {
        interactor.start()
    }

    override fun requestPermission(id: Int) {
        interactor.requestPermission(id)
    }

    override fun capture(
        posture: Posture,
        index: Int,
        option: Option
    ) {
        interactor.capture(
            posture = posture,
            index = index,
            option = option
        )
    }

    override fun stop() {
        interactor.stop()
    }
}
