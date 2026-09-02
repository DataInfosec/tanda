package com.tanda.account.remote.interceptor

import com.tanda.account.domain.usecase.auth.TokenUsecase
import io.ktor.client.plugins.HttpSendInterceptor
import io.ktor.http.encodedPath

fun createJwtInterceptor(
    usecase: TokenUsecase,
    excludedPaths: Set<String> = setOf(DEVICE_ACTIVATION_PATH),
): HttpSendInterceptor = { requestBuilder ->
    if (requestBuilder.url.encodedPath !in excludedPaths) {
        usecase()?.let {
            requestBuilder.headers.append(
                "Authorization",
                "Bearer $it"
            )
        }
    }
    execute(requestBuilder)
}

private const val DEVICE_ACTIVATION_PATH = "/v1/device-provisioning/instances"
