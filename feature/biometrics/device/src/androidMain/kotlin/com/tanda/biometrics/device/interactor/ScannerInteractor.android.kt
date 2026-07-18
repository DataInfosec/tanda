package com.tanda.biometrics.device.interactor

import android.content.Context
import android.content.Intent
import com.integratedbiometrics.ibscanultimate.IBScanDevice
import com.integratedbiometrics.ibscanultimate.IBScanDevice.OPTION_AUTO_CAPTURE
import com.integratedbiometrics.ibscanultimate.IBScanDevice.OPTION_AUTO_CONTRAST
import com.integratedbiometrics.ibscanultimate.IBScanDevice.OPTION_IGNORE_FINGER_COUNT
import com.integratedbiometrics.ibscanultimate.IBScanDeviceListener
import com.integratedbiometrics.ibscanultimate.IBScanException
import com.integratedbiometrics.ibscanultimate.IBScanListener
import com.tanda.biometrics.domain.exception.DeviceException
import com.tanda.biometrics.domain.exception.DeviceNotFoundException
import com.tanda.biometrics.domain.exception.PermissionException
import com.tanda.biometrics.device.mapper.toCaptureOption
import com.tanda.biometrics.device.mapper.toImageType
import com.tanda.biometrics.domain.model.Option
import com.tanda.biometrics.domain.model.Posture
import com.tanda.biometrics.domain.model.Status
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import scanner.ScannerFactory
import scanner.ScannerObservable
import scanner.ScannerObservableDelegate

actual class ScannerInteractor(
    private val context: Context,
    private val factory: ScannerFactory = ScannerFactory.Delegate(),
    private val observable: ScannerObservable = ScannerObservableDelegate(),
    private val listener: IBScanDeviceListener = observable as IBScanDeviceListener
) : IBScanListener, ScannerObservable by observable {
    private var id: Int? = null

    private var device: IBScanDevice? = null

    private val _status = MutableSharedFlow<Status>(replay = REPLAY)

    private val scanner by lazy { factory.create(context) }

    actual val status: Flow<Status> get() = _status

    actual fun start() {
        context.sendBroadcast(
            Intent().apply {
                action = ACTION_FINGER_CONFIG
                putExtra(STATUS_KEY, true)
            }
        )
        scanner.setScanListener(this)
    }

    actual fun hasPermission(id: Int): Boolean {
        return scanner.hasPermission(id)
    }

    override fun scanDeviceAttached(deviceId: Int) {
        this.id = deviceId
        _status.tryEmit(Status.Attached(deviceId))
    }

    override fun scanDeviceDetached(deviceId: Int) {
        this.id = null
        _status.tryEmit(Status.Detached(deviceId))
    }

    override fun scanDevicePermissionGranted(deviceId: Int, granted: Boolean) {
        if (!granted) {
            _status.tryEmit(Status.Error(PermissionException(deviceId)))
        }
    }

    override fun scanDeviceCountChanged(deviceCount: Int) {
        if (deviceCount <= 0) {
            _status.tryEmit(Status.Error(DeviceNotFoundException()))
        }
    }

    override fun scanDeviceInitProgress(deviceIndex: Int, progressValue: Int) {
        id?.let {
            _status.tryEmit(Status.Initialize(
                id = it,
                index = deviceIndex,
                progress = progressValue
            ))
        } ?: _status.tryEmit(Status.Error(DeviceNotFoundException()))
    }

    override fun scanDeviceOpenComplete(
        deviceIndex: Int,
        device: IBScanDevice?,
        exception: IBScanException?
    ) {
        if (exception != null) {
            _status.tryEmit(Status.Error(DeviceException(exception)))
        } else {
            id?.let {
                _status.tryEmit(Status.Ready(
                    id = it,
                    index = deviceIndex
                ))
            } ?: _status.tryEmit(Status.Error(DeviceNotFoundException()))
        }
    }

    actual fun requestPermission(id: Int) {
        scanner.requestPermission(id)
    }

    actual suspend fun capture(posture: Posture, index: Int, option: Option) {
        try {
            if (device != null) {
                device?.captureImageManually()
                return
            }
            device = scanner.openDevice(index)
            device?.setScanDeviceListener(listener)
            device?.beginCaptureImage(
                posture.toImageType(),
                IBScanDevice.ImageResolution.RESOLUTION_500,
                option.toCaptureOption() or
                        OPTION_AUTO_CONTRAST or
                        OPTION_AUTO_CAPTURE or
                        OPTION_IGNORE_FINGER_COUNT
            )
        } catch (exception: Throwable) {
            if (exception is IBScanException) {
                _status.tryEmit(Status.Error(DeviceException(exception)))
            } else {
                _status.tryEmit(Status.Error(exception))
            }
        }
    }

    actual fun stop() {
        context.sendBroadcast(
            Intent().apply {
                action = ACTION_FINGER_CONFIG
                putExtra(STATUS_KEY, false)
            }
        )
        scanner.setScanListener(null)
        device?.let { runCatching { it.close() } }
        id = null
        device = null
        _status.tryEmit(Status.Default)
    }

    private companion object {
        const val REPLAY = 1
        const val STATUS_KEY = "enable"
        const val ACTION_FINGER_CONFIG = "mtk.intent.ACTION_FINGER_CONFIG"
    }
}
