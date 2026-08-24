package com.tanda.attendance.data.remote

import com.tanda.attendance.data.model.DevicePointResponse
import com.tanda.attendance.exception.DeviceAuthorizationException
import com.tanda.core.remote.client.NetworkClient
import com.tanda.core.remote.exception.NetworkException
import io.ktor.client.HttpClient
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Named

@Factory
class DevicePointDelegate(
    @Named("deviceHttpClient") client: HttpClient,
) : NetworkClient(client), DevicePoint {
    override suspend fun get(): DevicePointResponse {
        val response = get<DevicePointResponse>(DEVICE_POINTS_PATH)
        if (response.isFailure) {
            val error = response.exceptionOrNull()
            if (error is DeviceAuthorizationException) throw error
            throw NetworkException(error?.message, error)
        }
        return response.getOrThrow()
    }

    private companion object {
        const val DEVICE_POINTS_PATH = "/v1/device/points"
    }
}
