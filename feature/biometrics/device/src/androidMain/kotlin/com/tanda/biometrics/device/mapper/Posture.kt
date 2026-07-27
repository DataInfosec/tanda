package com.tanda.biometrics.device.mapper

import com.integratedbiometrics.ibscanultimate.IBScanDevice
import com.tanda.biometrics.domain.model.Finger

fun Finger.toImageType(): IBScanDevice.ImageType = when (this) {
    Finger.TYPE_NONE -> IBScanDevice.ImageType.TYPE_NONE
    Finger.ROLL_SINGLE_FINGER -> IBScanDevice.ImageType.ROLL_SINGLE_FINGER
    Finger.FLAT_SINGLE_FINGER -> IBScanDevice.ImageType.FLAT_SINGLE_FINGER
    Finger.FLAT_TWO_FINGERS -> IBScanDevice.ImageType.FLAT_TWO_FINGERS
    Finger.FLAT_FOUR_FINGERS -> IBScanDevice.ImageType.FLAT_FOUR_FINGERS
    Finger.FLAT_THREE_FINGERS -> IBScanDevice.ImageType.FLAT_THREE_FINGERS
}
