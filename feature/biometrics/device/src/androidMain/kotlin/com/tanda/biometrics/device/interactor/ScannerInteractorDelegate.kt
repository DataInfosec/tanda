package com.tanda.biometrics.device.interactor

import com.integratedbiometrics.ibscanultimate.IBScan
import com.integratedbiometrics.ibscanultimate.IBScanDevice
import com.integratedbiometrics.ibscanultimate.IBScanException
import com.integratedbiometrics.ibscanultimate.IBScanListener

class ScannerInteractorDelegate(
    private val scanner: IBScan
) : ScannerInteractor, IBScanListener {
    override fun scanDeviceAttached(deviceId: Int) {
        TODO("Not yet implemented")
    }

    override fun scanDeviceDetached(deviceId: Int) {
        TODO("Not yet implemented")
    }

    override fun scanDevicePermissionGranted(deviceId: Int, granted: Boolean) {
        TODO("Not yet implemented")
    }

    override fun scanDeviceCountChanged(deviceCount: Int) {
        TODO("Not yet implemented")
    }

    override fun scanDeviceInitProgress(deviceIndex: Int, progressValue: Int) {
        TODO("Not yet implemented")
    }

    override fun scanDeviceOpenComplete(
        deviceIndex: Int,
        device: IBScanDevice?,
        exception: IBScanException?
    ) {
        TODO("Not yet implemented")
    }
}
