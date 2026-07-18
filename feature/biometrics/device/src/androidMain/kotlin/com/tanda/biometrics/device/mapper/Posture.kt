package com.tanda.biometrics.device.mapper

import com.integratedbiometrics.ibscanultimate.IBScanDevice
import com.tanda.biometrics.domain.model.Posture

fun Posture.toImageType(): IBScanDevice.ImageType = when (this) {
    Posture.TYPE_NONE -> IBScanDevice.ImageType.TYPE_NONE
    Posture.ROLL_SINGLE_FINGER -> IBScanDevice.ImageType.ROLL_SINGLE_FINGER
    Posture.FLAT_SINGLE_FINGER -> IBScanDevice.ImageType.FLAT_SINGLE_FINGER
    Posture.FLAT_TWO_FINGERS -> IBScanDevice.ImageType.FLAT_TWO_FINGERS
    Posture.FLAT_FOUR_FINGERS -> IBScanDevice.ImageType.FLAT_FOUR_FINGERS
    Posture.FLAT_THREE_FINGERS -> IBScanDevice.ImageType.FLAT_THREE_FINGERS
}
