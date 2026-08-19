package com.tanda.biometrics.domain.model

data class Subject(
    val createdAt: String,
    val credentialStatus: String,
    val displayName: String,
    val externalReference: String,
    val id: String,
    val lifecycleStatus: String,
    val organizationId: String,
    val profileCompleteness: String,
    val profileFields: Map<String, String>,
    val recordVerification: String,
    val siteId: String,
    val subjectType: String,
    val updatedAt: String,
    val version: Int
)
