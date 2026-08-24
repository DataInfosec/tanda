package com.tanda.biometrics.verification.mapper

import com.datainfosec.biometric.MobileIdentificationEvidence
import com.datainfosec.biometric.MobileIdentifyOutcome
import com.tanda.biometrics.domain.model.Capture

fun MobileIdentifyOutcome.Match.mapToDomain(): Capture {
    return Capture(
        id = evidence.subjectId,
        score = evidence.score,
        recordId = evidence.recordId,
        galleryId = evidence.galleryId,
        galleryRevision = evidence.galleryRevision,
        modality = evidence.modality,
        verificationScore = evidence.verificationScore,
        provisionalEnrollmentSubmissionId = evidence.provisionalEnrollmentSubmissionId,
        evidenceToken = evidence.evidenceToken
    )
}

fun Capture.mapToData(): MobileIdentificationEvidence{
    return MobileIdentificationEvidence(
        subjectId = id,
        score = score,
        recordId = recordId,
        galleryId = galleryId,
        galleryRevision = galleryRevision,
        modality = modality,
        verificationScore = verificationScore,
        provisionalEnrollmentSubmissionId = provisionalEnrollmentSubmissionId,
        evidenceToken = evidenceToken
    )
}
