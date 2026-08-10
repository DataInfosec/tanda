package com.tanda.biometrics.domain.exception

class FingerprintException(
    val score: Float,
    val threshold: Float,
    reason: String
) : Throwable(reason)
