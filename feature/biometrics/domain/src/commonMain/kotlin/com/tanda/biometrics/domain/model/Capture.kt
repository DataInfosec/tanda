package com.tanda.biometrics.domain.model

data class Capture(
    val id: String,
    val score: Float,
    val recordId: String,
    val galleryId: String,
    val galleryRevision: ULong,
    val modality: String,
    val verificationScore: Float,
    val provisionalEnrollmentSubmissionId: String?,
    val evidenceToken: String,
)
