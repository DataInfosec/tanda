package com.tanda.biometrics.domain.repository

import com.tanda.biometrics.domain.model.Mode
import com.tanda.biometrics.domain.model.Option
import com.tanda.biometrics.domain.model.Finger
import com.tanda.biometrics.domain.model.State
import com.tanda.biometrics.domain.model.Status
import kotlinx.coroutines.flow.Flow

interface ScannerRepository {
    val state: Flow<State>

    val status: Flow<Status>

    val mode: Flow<Mode>

    fun start()

    fun hasPermission(id: Int): Boolean

    fun requestPermission(id: Int)

    suspend fun capture(finger: Finger, index: Int, option: Option)

    fun stop()
}
