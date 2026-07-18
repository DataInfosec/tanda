package com.tanda.biometrics.device.interactor

import com.tanda.biometrics.domain.model.Option
import com.tanda.biometrics.domain.model.Posture
import com.tanda.biometrics.domain.model.Event
import com.tanda.biometrics.domain.model.State
import com.tanda.biometrics.domain.model.Status
import kotlinx.coroutines.flow.Flow

expect class ScannerInteractor {
    val state: Flow<State>

    val status: Flow<Status>

    val event: Flow<Event>

    fun start()

    fun requestPermission(id: Int)

    fun capture(posture: Posture, index: Int, option: Option)

    fun stop()
}
