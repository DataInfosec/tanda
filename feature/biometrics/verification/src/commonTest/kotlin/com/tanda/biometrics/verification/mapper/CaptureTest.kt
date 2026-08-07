package com.tanda.biometrics.verification.mapper

import com.datainfosec.biometric.MobileIdentifyOutcome
import com.datainfosec.biometric.MobileRetryReason
import com.tanda.biometrics.domain.model.IdentificationResult
import com.tanda.biometrics.domain.model.RetryReason
import kotlin.test.Test
import kotlin.test.assertEquals

class CaptureTest {
    @Test
    fun mapsWeakScoreToRetryWithoutThrowing() {
        val result = MobileIdentifyOutcome.Retry(
            reason = MobileRetryReason.WEAK_SCORE,
            bestScore = 0.25f,
            bestVerificationScore = 0.15f,
        ).mapToDomain()

        assertEquals(
            IdentificationResult.Retry(
                reason = RetryReason.WEAK_SCORE,
                bestScore = 0.25f,
                bestVerificationScore = 0.15f,
            ),
            result,
        )
    }
}
