package com.tanda.account.remote.interceptor

import com.tanda.account.domain.usecase.TokenUsecase
import io.ktor.client.plugins.HttpSendInterceptor

fun createJwtInterceptor(
    usecase: TokenUsecase
): HttpSendInterceptor = { requestBuilder ->
    usecase()?.let {
            requestBuilder.headers.append(
                "Authorization",
                "Bearer $it"
            )
        }
    execute(requestBuilder)
}
