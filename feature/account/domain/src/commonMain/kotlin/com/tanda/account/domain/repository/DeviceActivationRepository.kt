package com.tanda.account.domain.repository

import com.tanda.account.domain.model.DeviceActivation
import kotlinx.coroutines.flow.Flow

interface DeviceActivationRepository {
    suspend fun activate(activationCode: String): DeviceActivation

    fun observeDeviceId(): Flow<String?>

    fun getDeviceId(): String?

    fun observeDeviceToken(): Flow<String?>

    fun getDeviceToken(): String?

    fun observeGallerySyncUrl(): Flow<String?>

    fun getGallerySyncUrl(): String?
}
