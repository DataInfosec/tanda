package com.tanda.biometrics.domain.repository

import com.tanda.biometrics.domain.model.DeviceConfiguration
import kotlinx.coroutines.flow.Flow

interface DeviceConfigurationRepository {
    fun get(): DeviceConfiguration?

    fun observe(): Flow<DeviceConfiguration?>

    fun save(configuration: DeviceConfiguration)
}
