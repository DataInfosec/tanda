package com.tanda.account.remote.api

import com.tanda.account.data.api.device.DeviceActivationApi
import com.tanda.account.data.model.device.DeviceActivation
import com.tanda.account.remote.mapper.mapToData
import com.tanda.account.remote.model.device.DeviceActivationModel
import com.tanda.account.remote.payload.DeviceActivationPayload
import com.tanda.core.remote.client.NetworkClient
import com.tanda.core.remote.exception.NetworkException
import io.ktor.client.HttpClient

class DeviceActivationApiDelegate(client: HttpClient) : NetworkClient(client), DeviceActivationApi {
    override suspend fun activate(activationCode: String): DeviceActivation {
        val response = post<DeviceActivationModel, DeviceActivationPayload>(
            url = DEVICE_ACTIVATION_PATH,
            body = DeviceActivationPayload(activationCode)
        )
        if (response.isFailure) {
            val error = response.exceptionOrNull()
            throw NetworkException(error?.message, error)
        }
        return response.getOrThrow().mapToData()
    }

    private companion object {
        const val DEVICE_ACTIVATION_PATH = "/v1/device-provisioning/instances"
    }
}
