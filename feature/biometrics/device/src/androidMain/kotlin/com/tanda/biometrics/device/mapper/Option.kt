package com.tanda.biometrics.device.mapper

import com.integratedbiometrics.ibscanultimate.IBScanDevice
import com.tanda.biometrics.domain.model.Option

fun Option.toCaptureOption(): Int = when (this) {
    Option.AUTO_CONTRAST -> IBScanDevice.OPTION_AUTO_CONTRAST
    Option.AUTO_CAPTURE -> IBScanDevice.OPTION_AUTO_CAPTURE
    Option.IGNORE_FINGER_COUNT -> IBScanDevice.OPTION_IGNORE_FINGER_COUNT
}
