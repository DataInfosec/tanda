package com.tanda.biometrics.verification.mapper

import com.datainfosec.biometric.MobileIdentifyOutcome
import com.tanda.biometrics.domain.model.Capture

fun MobileIdentifyOutcome.Match.mapToDomain(): Capture {
    return Capture(
        id = studentId,
        score = score
    )
}
