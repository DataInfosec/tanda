package com.tanda.biometrics.device.interactor

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import com.integratedbiometrics.ibscanultimate.IBScan
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
import com.tanda.biometrics.domain.exception.ScannerException
import com.tanda.biometrics.domain.model.Finger
import com.tanda.biometrics.domain.model.Option
import com.tanda.biometrics.domain.model.Status
import com.tanda.biometrics.device.mapper.toCaptureOption
import com.tanda.biometrics.device.mapper.toImageType
import com.tanda.biometrics.device.scanner.ScannerFactory
import com.tanda.biometrics.device.scanner.ScannerObservable
import com.tanda.biometrics.device.scanner.ScannerObservableDelegate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withTimeoutOrNull

actual class ScannerInteractor(
    private val context: Context,
    private val factory: ScannerFactory = ScannerFactory.Delegate(),
    private val observable: ScannerObservable = ScannerObservableDelegate(),
    private val listener: IBScanDeviceListener = observable as IBScanDeviceListener
) : IBScanListener, ScannerObservable by observable {
    private var id: Int? = null

    private var device: IBScanDevice? = null

    @Volatile
    private var permissionResult: CompletableDeferred<Boolean>? = null

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
        syncAttachedDevices()
    }

    private fun syncAttachedDevices() {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        usbManager.deviceList.values
            .firstOrNull(IBScan::isScanDevice)
            ?.let { scanDeviceAttached(it.deviceId) }
            ?: _status.tryEmit(Status.Error(DeviceNotFoundException()))
    }

    actual fun hasPermission(id: Int): Boolean {
        return scanner.hasPermission(id)
    }

    override fun scanDeviceAttached(deviceId: Int) {
        this.id = deviceId
        _status.tryEmit(Status.Attached(deviceId))
    }

    override fun scanDeviceDetached(deviceId: Int) {
        device?.let { closeWithRetry(it) }
        permissionResult?.complete(false)
        permissionResult = null
        id = null
        device = null
        _status.tryEmit(Status.Detached(deviceId))
    }

    private fun closeWithRetry(device: IBScanDevice, attempts: Int = 3) {
        repeat(attempts) {
            try {
                device.close()
                return
            } catch (exception: IBScanException) {
                if (exception.type != IBScanException.Type.RESOURCE_LOCKED) return
            }
        }
    }

    override fun scanDevicePermissionGranted(deviceId: Int, granted: Boolean) {
        permissionResult?.complete(granted)
        permissionResult = null
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
        if (scanner.hasPermission(id)) return
        permissionResult?.complete(false)
        permissionResult = CompletableDeferred()
        scanner.requestPermission(id)
    }

    actual suspend fun capture(finger: Finger, index: Int, option: Option) {
        try {
            observable.reset()
            val deviceId = id ?: throw DeviceNotFoundException()
            val hasPermission = scanner.hasPermission(deviceId) || withTimeoutOrNull(
                PERMISSION_TIMEOUT_MILLIS
            ) {
                permissionResult?.await()
            } == true
            if (!hasPermission) {
                throw PermissionException(deviceId)
            }
            if (device == null) {
                device = scanner.openDevice(index)
                device?.setScanDeviceListener(listener)
            }
            if (device?.isCaptureActive == true) {
                device?.captureImageManually()
            } else {
                device?.beginCaptureImage(
                    finger.toImageType(),
                    IBScanDevice.ImageResolution.RESOLUTION_500,
                    option.toCaptureOption() or
                            OPTION_AUTO_CONTRAST or
                            OPTION_AUTO_CAPTURE or
                            OPTION_IGNORE_FINGER_COUNT
                )
            }
        } catch (exception: Throwable) {
            releaseDevice()
            val error = if (exception is IBScanException) {
                ScannerException(exception.type.name, exception)
            } else exception
            _status.tryEmit(Status.Error(error))
            throw error
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
        permissionResult?.complete(false)
        permissionResult = null
        releaseDevice()
        id = null
        observable.reset()
        _status.tryEmit(Status.Default)
    }

    private fun releaseDevice() {
        val current = device ?: return
        runCatching { if (current.isCaptureActive) current.cancelCaptureImage() }
        closeWithRetry(current)
        device = null
    }

    private companion object {
        const val REPLAY = 1
        const val PERMISSION_TIMEOUT_MILLIS = 30_000L
        const val STATUS_KEY = "enable"
        const val ACTION_FINGER_CONFIG = "mtk.intent.ACTION_FINGER_CONFIG"
    }
}
