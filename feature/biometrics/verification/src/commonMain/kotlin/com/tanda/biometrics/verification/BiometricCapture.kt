package com.tanda.biometrics.verification

import com.tanda.biometrics.domain.model.Image

private const val CAPTURE_WIDTH = 400
private const val CAPTURE_HEIGHT = 500
private const val CAPTURE_BYTES = CAPTURE_WIDTH * CAPTURE_HEIGHT

/**
 * Validate scanner output before passing it to `MobileBiometricSdk`.
 *
 * Scanner discovery and capture remain owned by the vendor integration. The
 * biometric SDK accepts only the raw pixels from one 400x500, 8-bit grayscale
 * image.
 */
fun Image.biometricSdkBytes(): ByteArray {
    require(width == CAPTURE_WIDTH && height == CAPTURE_HEIGHT) {
        "Fingerprint capture must be ${CAPTURE_WIDTH}x$CAPTURE_HEIGHT, got ${width}x$height"
    }
    require(data.size == CAPTURE_BYTES) {
        "Fingerprint capture must contain $CAPTURE_BYTES grayscale bytes, got ${data.size}"
    }
    return data
}
