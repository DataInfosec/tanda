package com.tanda.biometrics.device.interactor

import com.integratedbiometrics.ibscanultimate.IBScanDevice
import com.integratedbiometrics.ibscanultimate.IBScanDeviceListener
import com.integratedbiometrics.ibscanultimate.IBScanException

class DeviceInteractor : IBScanDeviceListener {
    override fun deviceCommunicationBroken(device: IBScanDevice?) {
        TODO("Not yet implemented")
    }

    override fun deviceImagePreviewAvailable(
        device: IBScanDevice?,
        image: IBScanDevice.ImageData?
    ) {
        TODO("Not yet implemented")
    }

    override fun deviceFingerCountChanged(
        device: IBScanDevice?,
        fingerState: IBScanDevice.FingerCountState?
    ) {
        TODO("Not yet implemented")
    }

    override fun deviceFingerQualityChanged(
        device: IBScanDevice?,
        fingerQualities: Array<out IBScanDevice.FingerQualityState?>?
    ) {
        TODO("Not yet implemented")
    }

    override fun deviceAcquisitionBegun(
        device: IBScanDevice?,
        imageType: IBScanDevice.ImageType?
    ) {
        TODO("Not yet implemented")
    }

    override fun deviceAcquisitionCompleted(
        device: IBScanDevice?,
        imageType: IBScanDevice.ImageType?
    ) {
        TODO("Not yet implemented")
    }

    override fun deviceImageResultAvailable(
        device: IBScanDevice?,
        image: IBScanDevice.ImageData?,
        imageType: IBScanDevice.ImageType?,
        splitImageArray: Array<out IBScanDevice.ImageData?>?
    ) {
        TODO("Not yet implemented")
    }

    override fun deviceImageResultExtendedAvailable(
        device: IBScanDevice?,
        imageStatus: IBScanException?,
        image: IBScanDevice.ImageData?,
        imageType: IBScanDevice.ImageType?,
        detectedFingerCount: Int,
        segmentImageArray: Array<out IBScanDevice.ImageData?>?,
        segmentPositionArray: Array<out IBScanDevice.SegmentPosition?>?
    ) {
        TODO("Not yet implemented")
    }

    override fun devicePlatenStateChanged(
        device: IBScanDevice?,
        platenState: IBScanDevice.PlatenState?
    ) {
        TODO("Not yet implemented")
    }

    override fun deviceWarningReceived(
        device: IBScanDevice?,
        warning: IBScanException?
    ) {
        TODO("Not yet implemented")
    }

    override fun devicePressedKeyButtons(
        device: IBScanDevice?,
        pressedKeyButtons: Int
    ) {
        TODO("Not yet implemented")
    }
}
