package com.tanda.core.remote.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSendInterceptor

expect fun getHttpClient(
    baseUrl: String,
    interceptors: List<HttpSendInterceptor>
): HttpClient
