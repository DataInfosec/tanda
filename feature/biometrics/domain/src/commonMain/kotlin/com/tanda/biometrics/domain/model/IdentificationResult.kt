package com.tanda.biometrics.domain.model

sealed interface IdentificationResult {
    data class Match(val capture: Capture) : IdentificationResult

    data class Retry(
        val reason: RetryReason,
        val bestScore: Float?,
        val bestVerificationScore: Float?,
    ) : IdentificationResult
}

enum class RetryReason {
    LOW_QUALITY,
    NO_CANDIDATES,
    WEAK_SCORE,
    AMBIGUOUS,
}
