package com.tanda.attendance.network

import com.tanda.attendance.exception.DeviceAuthorizationException
import com.tanda.biometrics.domain.repository.DeviceConfigurationRepository
import io.ktor.client.plugins.HttpSendInterceptor
import io.ktor.http.HttpHeaders

fun createDeviceAuthorizationInterceptor(
    repository: DeviceConfigurationRepository,
): HttpSendInterceptor = { requestBuilder ->
    val token = repository.get()?.fingerprintToken.orEmpty()
    if (token.isBlank()) {
        throw DeviceAuthorizationException("Device token is not configured")
    }

    println("token: $token")
    requestBuilder.headers.remove(HttpHeaders.Authorization)
    requestBuilder.headers.append(HttpHeaders.Authorization, "Bearer $token")
    val call = execute(requestBuilder)
    println("device config response: ${call.response}")
    if (call.response.status.value == 401) {
        throw DeviceAuthorizationException("Device authorization has expired")
    }
    call
}
