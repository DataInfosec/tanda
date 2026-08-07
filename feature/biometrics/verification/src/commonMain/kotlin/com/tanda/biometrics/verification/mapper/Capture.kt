package com.tanda.biometrics.verification.mapper

import com.datainfosec.biometric.MobileIdentifyOutcome
import com.datainfosec.biometric.MobileRetryReason
import com.tanda.biometrics.domain.model.Capture
import com.tanda.biometrics.domain.model.IdentificationResult
import com.tanda.biometrics.domain.model.RetryReason

fun MobileIdentifyOutcome.mapToDomain(): IdentificationResult {
    return when (this) {
        is MobileIdentifyOutcome.Match -> IdentificationResult.Match(
            Capture(
                id = subjectId,
                score = score,
            )
        )
        is MobileIdentifyOutcome.Retry -> IdentificationResult.Retry(
            reason = reason.mapToDomain(),
            bestScore = bestScore,
            bestVerificationScore = bestVerificationScore,
        )
    }
}

private fun MobileRetryReason.mapToDomain(): RetryReason {
    return when (this) {
        MobileRetryReason.LOW_QUALITY -> RetryReason.LOW_QUALITY
        MobileRetryReason.NO_CANDIDATES -> RetryReason.NO_CANDIDATES
        MobileRetryReason.WEAK_SCORE -> RetryReason.WEAK_SCORE
        MobileRetryReason.AMBIGUOUS -> RetryReason.AMBIGUOUS
    }
}
