package com.tanda.biometrics.domain.exception

class FingerprintException(
    val score: Float,
    reason: String
) : Throwable(reason)
