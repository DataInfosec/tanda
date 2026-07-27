package com.tanda.biometrics.data.repository

import com.tanda.biometrics.data.device.ScannerDevice
import com.tanda.biometrics.domain.model.Mode
import com.tanda.biometrics.domain.model.Option
import com.tanda.biometrics.domain.model.Finger
import com.tanda.biometrics.domain.model.State
import com.tanda.biometrics.domain.model.Status
import com.tanda.biometrics.domain.repository.ScannerRepository
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Singleton

@Singleton
class ScannerRepositoryDelegate(
    private val device: ScannerDevice
) : ScannerRepository {
    override val state: Flow<State> get() = device.state

    override val status: Flow<Status> get() = device.status

    override val mode: Flow<Mode> get() = device.mode

    override fun start() {
        device.start()
    }

    override fun hasPermission(id: Int): Boolean {
        return device.hasPermission(id)
    }

    override fun requestPermission(id: Int) {
        device.requestPermission(id)
    }

    override suspend fun capture(
        finger: Finger,
        index: Int,
        option: Option
    ) {
        device.capture(finger, index, option)
    }

    override fun stop() {
        device.stop()
    }
}
