package com.tanda.biometrics.device.scanner

import com.integratedbiometrics.ibscanultimate.IBScanDevice
import com.tanda.biometrics.domain.model.Mode
import com.tanda.biometrics.domain.model.Snapshot
import kotlinx.coroutines.flow.Flow

interface ScannerObservable {
    val state: Flow<Snapshot>

    val mode: Flow<Mode>

    fun post(image: IBScanDevice.ImageData)

    fun reset()
}
