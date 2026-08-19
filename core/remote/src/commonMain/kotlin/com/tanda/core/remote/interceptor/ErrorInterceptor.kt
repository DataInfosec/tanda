package com.tanda.core.remote.interceptor

import com.tanda.core.remote.exception.BusinessException
import com.tanda.core.remote.exception.AuthorizationException
import io.ktor.client.plugins.HttpSendInterceptor
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun createErrorInterceptor(
    onUnauthorized: () -> Unit = {}
): HttpSendInterceptor = { requestBuilder ->
    val call = execute(requestBuilder)
    val response = call.response
    val statusCode = response.status.value
    if (statusCode == 401) {
        onUnauthorized()
        throw AuthorizationException("Session expired")
    }
    if (statusCode in listOf(400, 401, 409)) {
        val bodyText = response.bodyAsText()
        try {
            val jsonObject = Json.parseToJsonElement(bodyText).jsonObject
            val code = jsonObject["code"]?.jsonPrimitive?.content
            val message = jsonObject["message"]?.jsonPrimitive?.content
            if (code != null && message != null) {
                throw BusinessException(
                    code = code,
                    message = message
                )
            }
        } catch (e: BusinessException) {
            throw e
        } catch (_: Throwable) {
            // Ignore other parsing exceptions to let normal execution continue,
            // though the response stream might be consumed by bodyAsText()
        }
    }
    call
}
