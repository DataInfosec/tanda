package com.tanda.biometrics.device.scanner

import com.tanda.biometrics.domain.model.Mode
import com.tanda.biometrics.domain.model.Snapshot
import kotlinx.coroutines.flow.Flow

interface ScannerObservable {
    val state: Flow<Snapshot>

    val mode: Flow<Mode>

    fun reset()
}
