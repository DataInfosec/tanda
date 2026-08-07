package com.tanda.attendance.remote

import com.datainfosec.biometric.MobileDeviceTokenProvider
import com.tanda.core.remote.client.NetworkClient
import com.tanda.core.remote.exception.NetworkException
import io.ktor.client.HttpClient
import org.koin.core.annotation.Factory

@Factory
class EnrollmentApiDelegate(
    client: HttpClient,
    private val deviceTokenProvider: MobileDeviceTokenProvider,
) : NetworkClient(client), EnrollmentApi {
    override suspend fun start(
        externalReference: String,
        idempotencyKey: String,
    ): EnrollmentStartResult {
        val response = post<EnrollmentStartResult, StartEnrollmentRequest>(
            url = ENROLLMENT_PATH,
            body = StartEnrollmentRequest(
                externalReference = externalReference,
                idempotencyKey = idempotencyKey,
            ),
            headers = mapOf(
                DEVICE_AUTHORIZATION_HEADER to "Bearer ${deviceTokenProvider.currentToken()}",
            ),
        )
        if (response.isFailure) {
            val error = response.exceptionOrNull()
            throw NetworkException(error?.message, error)
        }
        return response.getOrThrow()
    }

    private companion object {
        const val ENROLLMENT_PATH = "/v1/admin/enrollments"
        const val DEVICE_AUTHORIZATION_HEADER = "X-Tanda-Device-Authorization"
    }
}
