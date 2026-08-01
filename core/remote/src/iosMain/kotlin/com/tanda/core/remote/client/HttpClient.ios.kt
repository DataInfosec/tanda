package com.tanda.core.remote.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpSendInterceptor
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.plugin
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

actual fun getHttpClient(
    baseUrl: String,
    interceptors: List<HttpSendInterceptor>
): HttpClient {
    return HttpClient {
        defaultRequest { url(baseUrl) }
        install(HttpCookies) {
            storage = AcceptAllCookiesStorage()
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
        install(HttpSend)
    }.apply {
        val httpSendPlugin = plugin(HttpSend)
        interceptors.forEach {
            httpSendPlugin.intercept { request ->
                it.invoke(this, request)
            }
        }
    }
}
