package com.tanda.account.data.api.device

import com.tanda.account.data.model.device.DeviceActivation

interface DeviceActivationApi {
    suspend fun activate(activationCode: String): DeviceActivation
}
