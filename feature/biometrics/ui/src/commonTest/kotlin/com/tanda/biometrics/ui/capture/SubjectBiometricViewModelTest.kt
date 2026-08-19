package com.tanda.biometrics.ui.capture

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubjectBiometricViewModelTest {
    @Test
    fun proceedsToEnrollmentWhenIdentificationHasNoAcceptedMatch() {
        assertTrue(shouldProceedToEnrollment("NO_CANDIDATES"))
        assertTrue(shouldProceedToEnrollment("WEAK_SCORE"))
    }

    @Test
    fun retriesWhenCaptureOrIdentificationIsInconclusive() {
        assertFalse(shouldProceedToEnrollment("LOW_QUALITY"))
        assertFalse(shouldProceedToEnrollment("AMBIGUOUS"))
        assertFalse(shouldProceedToEnrollment(null))
    }
}
