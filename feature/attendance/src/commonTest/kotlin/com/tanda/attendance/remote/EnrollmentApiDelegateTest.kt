package com.tanda.attendance.remote

import com.datainfosec.biometric.MobileDeviceTokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EnrollmentApiDelegateTest {
    @Test
    fun sendsDeviceAuthorizationAndDecodesServerAuthorization() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/v1/admin/enrollments", request.url.encodedPath)
            assertEquals(
                "Bearer provisioned-device-token",
                request.headers["X-Tanda-Device-Authorization"],
            )
            assertEquals("Bearer admin-session-token", request.headers[HttpHeaders.Authorization])
            val requestBody = (request.body as TextContent).text
            assertEquals(
                "{\"external_reference\":\"ADM-42\",\"idempotency_key\":\"tap-1\"}",
                requestBody,
            )
            respond(
                content = """
                    {
                      "subject_id": "subject-42",
                      "credential_status": "pending",
                      "capture_required": true,
                      "authorization": {
                        "enrollment_operation_id": "operation-42",
                        "performed_by": "admin-1",
                        "device_instance_id": "instance-1",
                        "gallery_id": "gallery-1",
                        "subject_id": "subject-42",
                        "batch_id": null,
                        "authorization_expires_at": "2026-08-06T15:00:00Z"
                      }
                    }
                """.trimIndent(),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpClient(engine) {
            defaultRequest {
                header(HttpHeaders.Authorization, "Bearer admin-session-token")
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val tokenProvider = object : MobileDeviceTokenProvider {
            override fun currentToken(): String = "provisioned-device-token"
        }

        val result = EnrollmentApiDelegate(client, tokenProvider).start(
            externalReference = "ADM-42",
            idempotencyKey = "tap-1",
        )

        assertEquals("subject-42", result.subjectId)
        assertEquals("operation-42", assertNotNull(result.authorization).enrollmentOperationId)
    }
}
