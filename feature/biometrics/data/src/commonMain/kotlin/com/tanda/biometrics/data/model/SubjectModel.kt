package com.tanda.biometrics.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SubjectModel(
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("credential_status")
    val credentialStatus: String = "",
    @SerialName("display_name")
    val displayName: String = "",
    @SerialName("external_reference")
    val externalReference: String = "",
    val id: String = "",
    @SerialName("lifecycle_status")
    val lifecycleStatus: String = "",
    @SerialName("organization_id")
    val organizationId: String = "",
    @SerialName("profile_completeness")
    val profileCompleteness: String = "",
    @SerialName("profile_fields")
    val profileFields: JsonObject = JsonObject(emptyMap()),
    @SerialName("record_verification")
    val recordVerification: String = "",
    @SerialName("site_id")
    val siteId: String = "",
    @SerialName("subject_type")
    val subjectType: String = "",
    @SerialName("updated_at")
    val updatedAt: String = "",
    val version: Int = 0
)
