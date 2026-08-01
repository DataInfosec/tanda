package com.tanda.core.remote.client

import com.tanda.core.remote.exception.AuthorizationException
import com.tanda.core.remote.exception.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

abstract class NetworkClient(val client: HttpClient) {
    /**
     * Performs a GET request and parses the response
     * @param url The endpoint URL
     * @param headers Optional headers to include in the request
     * @return Result containing either the parsed response or an exception
     */
    suspend inline fun <reified T> get(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): Result<T> = request {
        val response = client.get(url) {
            headers.forEach { (key, value) ->
                header(key, value)
            }
        }
        when (response.status.value) {
            in 400..499 -> throw AuthorizationException(response.body())
            in 500..599 -> throw NetworkException(response.body())
        }
        response.body<T>()
    }

    /**
     * Performs a POST request and parses the response
     * @param url The endpoint URL
     * @param body The request body to send
     * @param headers Optional headers to include in the request
     * @return Result containing either the parsed response or an exception
     */
    suspend inline fun <reified T, reified R> post(
        url: String,
        body: R,
        headers: Map<String, String> = emptyMap()
    ): Result<T> = request {
        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            headers.forEach { (key, value) ->
                header(key, value)
            }
            if (body !is Unit) {
                setBody(body)
            }
        }
        when (response.status.value) {
            in 400..499 -> throw AuthorizationException(response.body())
            in 500..599 -> throw NetworkException(response.body())
        }
        response.body<T>()
    }

    suspend inline fun <reified T> post(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): Result<T> = post<T, Unit>(url, Unit, headers)

    /**
     * Performs a PUT request and parses the response
     * @param url The endpoint URL
     * @param body The request body to send
     * @param headers Optional headers to include in the request
     * @return Result containing either the parsed response or an exception
     */
    suspend inline fun <reified T, reified R> put(
        url: String,
        body: R,
        headers: Map<String, String> = emptyMap()
    ): Result<T> = request {
        val response = client.put(url) {
            contentType(ContentType.Application.Json)
            headers.forEach { (key, value) ->
                header(key, value)
            }
            setBody(body)
        }
        when (response.status.value) {
            in 400..499 -> throw AuthorizationException(response.body())
            in 500..599 -> throw NetworkException(response.body())
        }
        response.body<T>()
    }

    /**
     * Performs a DELETE request and parses the response
     * @param url The endpoint URL
     * @param headers Optional headers to include in the request
     * @return Result containing either the parsed response or an exception
     */
    suspend inline fun <reified T> delete(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): Result<T> = request {
        val response = client.delete(url) {
            headers.forEach { (key, value) ->
                header(key, value)
            }
        }
        val body = response.body<T>()
        when (response.status.value) {
            in 400..499 -> throw AuthorizationException(body.toString())
            in 500..599 -> throw NetworkException(body.toString())
        }
        body!!
    }

    suspend inline fun <reified T, reified R> delete(
        url: String,
        body: R,
        headers: Map<String, String> = emptyMap()
    ): Result<T> = request {
        val response = client.delete(url) {
            contentType(ContentType.Application.Json)
            headers.forEach { (key, value) ->
                header(key, value)
            }
            setBody(body)
        }
        when (response.status.value) {
            in 400..499 -> throw AuthorizationException(response.body())
            in 500..599 -> throw NetworkException(response.body())
        }
        response.body<T>()
    }

    /**
     * Wrapper for safe network requests that catch exceptions
     * @param block The network request to execute
     * @return Result containing either the response or an exception
     */
    suspend inline fun <T> request(crossinline block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}