package com.tanda.attendance.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class StartEnrollmentRequest(
    @SerialName("external_reference")
    val externalReference: String,
    @SerialName("idempotency_key")
    val idempotencyKey: String,
)

@Serializable
data class EnrollmentStartResult(
    @SerialName("subject_id")
    val subjectId: String,
    @SerialName("credential_status")
    val credentialStatus: String,
    @SerialName("capture_required")
    val captureRequired: Boolean,
    val authorization: EnrollmentAuthorization? = null,
)

@Serializable
data class EnrollmentAuthorization(
    @SerialName("enrollment_operation_id")
    val enrollmentOperationId: String,
    @SerialName("device_instance_id")
    val deviceInstanceId: String,
    @SerialName("gallery_id")
    val galleryId: String,
    @SerialName("subject_id")
    val subjectId: String,
    @SerialName("authorization_expires_at")
    val authorizationExpiresAt: String,
)
