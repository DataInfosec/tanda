package com.tanda.biometrics.device.interactor

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.SystemClock
import android.util.Log
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
import com.tanda.biometrics.device.mapper.toCaptureOption
import com.tanda.biometrics.device.mapper.toImageType
import com.tanda.biometrics.domain.exception.ScannerException
import com.tanda.biometrics.domain.model.Option
import com.tanda.biometrics.domain.model.Finger
import com.tanda.biometrics.domain.model.Status
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import com.tanda.biometrics.device.scanner.ScannerFactory
import com.tanda.biometrics.device.scanner.ScannerObservable
import com.tanda.biometrics.device.scanner.ScannerObservableDelegate

actual class ScannerInteractor(
    private val context: Context,
    private val factory: ScannerFactory = ScannerFactory.Delegate(),
    private val observable: ScannerObservable = ScannerObservableDelegate(),
    private val listener: IBScanDeviceListener = observable as IBScanDeviceListener
) : IBScanListener, ScannerObservable by observable {
    private var id: Int? = null

    private var device: IBScanDevice? = null

    private val deviceIndexes = mutableMapOf<Int, Int>()

    @Volatile
    private var started = false

    private val _status = MutableSharedFlow<Status>(replay = REPLAY)

    private val scanner by lazy { factory.create(context) }

    actual val status: Flow<Status> get() = _status

    @Synchronized
    actual fun start() {
        if (started) {
            Log.d(TAG, "start ignored; scanner session is already active")
            return
        }
        started = true
        Log.i(TAG, "starting scanner session")
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
        val attachedScanners = usbManager.deviceList.values.filter(IBScan::isScanDevice)
        Log.i(TAG, "found ${attachedScanners.size} attached scanner(s)")
        if (attachedScanners.isEmpty()) {
            _status.tryEmit(Status.Error(DeviceNotFoundException()))
            return
        }

        attachedScanners.forEach { usbDevice ->
            handleDeviceAttached(
                deviceId = usbDevice.deviceId,
                deviceIndex = findDeviceIndex(usbDevice.deviceId),
            )
        }
    }

    actual fun hasPermission(id: Int): Boolean {
        return scanner.hasPermission(id)
    }

    override fun scanDeviceAttached(deviceId: Int) {
        if (!started) return
        Log.i(TAG, "scanner attached: deviceId=$deviceId")
        handleDeviceAttached(deviceId, findDeviceIndex(deviceId))
    }

    private fun handleDeviceAttached(
        deviceId: Int,
        deviceIndex: Int?,
    ) {
        this.id = deviceId
        deviceIndex?.let { deviceIndexes[deviceId] = it }
        _status.tryEmit(Status.Attached(deviceId))
        val hasPermission = scanner.hasPermission(deviceId)
        Log.i(
            TAG,
            "handling scanner: deviceId=$deviceId, index=$deviceIndex, permission=$hasPermission",
        )
        if (hasPermission) {
            initializeDevice(deviceId)
        } else {
            Log.i(TAG, "requesting USB permission: deviceId=$deviceId")
            scanner.requestPermission(deviceId)
        }
    }

    private fun findDeviceIndex(deviceId: Int): Int? {
        val count = runCatching { scanner.getDeviceCount() }.getOrDefault(0)
        if (count == 1) {
            Log.d(TAG, "using sole SDK scanner for Android deviceId=$deviceId")
            return 0
        }

        val matchingIndex = (0 until count).firstOrNull { index ->
            runCatching {
                val description = scanner.getDeviceDescription(index)
                Log.d(
                    TAG,
                    "SDK scanner description: index=$index, deviceId=${description.deviceId}",
                )
                description.deviceId == deviceId
            }.getOrDefault(false)
        }
        if (matchingIndex != null) return matchingIndex
        return null
    }

    @Synchronized
    private fun initializeDevice(deviceId: Int) {
        if (device != null) {
            Log.d(TAG, "initialization ignored; scanner is already open")
            return
        }
        val index = deviceIndexes[deviceId]
            ?: findDeviceIndex(deviceId)
            ?: run {
                Log.i(TAG, "waiting for SDK index: deviceId=$deviceId")
                return
            }
        try {
            Log.i(TAG, "opening scanner: deviceId=$deviceId, index=$index")
            val openedDevice = scanner.openDevice(index)
            openedDevice.setScanDeviceListener(listener)
            device = openedDevice
            Log.i(TAG, "scanner ready: deviceId=$deviceId, index=$index")
            _status.tryEmit(Status.Ready(id = deviceId, index = index))
        } catch (error: Throwable) {
            Log.e(TAG, "scanner initialization failed: deviceId=$deviceId, index=$index", error)
            _status.tryEmit(Status.Error(error))
        }
    }

    override fun scanDeviceDetached(deviceId: Int) {
        if (!started) return
        Log.w(TAG, "scanner detached: deviceId=$deviceId")
        device?.let { closeWithRetry(it) }
        id = null
        device = null
        deviceIndexes.remove(deviceId)
        _status.tryEmit(Status.Detached(deviceId))
    }

    private fun closeWithRetry(device: IBScanDevice, attempts: Int = CLOSE_ATTEMPTS): Boolean {
        repeat(attempts) { attempt ->
            try {
                device.close()
                Log.i(TAG, "scanner device closed")
                return true
            } catch (exception: IBScanException) {
                if (exception.type != IBScanException.Type.RESOURCE_LOCKED) {
                    Log.e(TAG, "scanner close failed", exception)
                    return false
                }
                if (attempt < attempts - 1) {
                    SystemClock.sleep(CLOSE_RETRY_DELAY_MS)
                } else {
                    Log.e(TAG, "scanner remained resource-locked after $attempts close attempts")
                }
            }
        }
        return false
    }

    override fun scanDevicePermissionGranted(deviceId: Int, granted: Boolean) {
        if (!started) return
        Log.i(TAG, "USB permission result: deviceId=$deviceId, granted=$granted")
        if (granted) {
            initializeDevice(deviceId)
        } else {
            _status.tryEmit(Status.Error(PermissionException(deviceId)))
        }
    }

    override fun scanDeviceCountChanged(deviceCount: Int) {
        if (!started) return
        Log.i(TAG, "scanner count changed: count=$deviceCount")
        if (deviceCount <= 0) {
            _status.tryEmit(Status.Error(DeviceNotFoundException()))
            return
        }

        val deviceId = id ?: return
        findDeviceIndex(deviceId)?.let { index ->
            deviceIndexes[deviceId] = index
            if (scanner.hasPermission(deviceId)) {
                initializeDevice(deviceId)
            }
        }
    }

    override fun scanDeviceInitProgress(deviceIndex: Int, progressValue: Int) {
        if (!started) return
        Log.d(TAG, "scanner initialization: index=$deviceIndex, progress=$progressValue")
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
        if (!started) {
            device?.let { closeWithRetry(it) }
            return
        }
        if (exception != null) {
            Log.e(TAG, "asynchronous scanner open failed: index=$deviceIndex", exception)
            _status.tryEmit(Status.Error(DeviceException(exception)))
        } else {
            if (device != null) {
                this.device = device
                device.setScanDeviceListener(listener)
            }
            id?.let {
                Log.i(TAG, "asynchronous scanner open completed: deviceId=$it, index=$deviceIndex")
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

    actual suspend fun capture(finger: Finger, index: Int, option: Option) {
        try {
            observable.reset()
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
            val error = if (exception is IBScanException) {
                ScannerException(exception.type.name)
            } else {
                exception
            }
            _status.tryEmit(Status.Error(error))
            throw error
        }
    }

    @Synchronized
    actual fun stop() {
        if (!started && device == null) {
            Log.d(TAG, "stop ignored; scanner session is not active")
            return
        }
        Log.i(TAG, "stopping scanner session")

        // Ignore late SDK callbacks as soon as lifecycle teardown begins.
        started = false
        val openDevice = device
        device = null

        openDevice?.let { scannerDevice ->
            val captureActive = runCatching { scannerDevice.isCaptureActive }
                .onFailure { Log.w(TAG, "could not read capture state during stop", it) }
                .getOrDefault(false)
            if (captureActive) {
                runCatching { scannerDevice.cancelCaptureImage() }
                    .onFailure { Log.w(TAG, "could not cancel active capture during stop", it) }
                SystemClock.sleep(CAPTURE_CANCEL_DELAY_MS)
            }
            scannerDevice.setScanDeviceListener(null)
            closeWithRetry(scannerDevice)
        }
        scanner.setScanListener(null)

        // Change the vendor USB mode only after the native scanner handle is closed.
        context.sendBroadcast(
            Intent(ACTION_FINGER_CONFIG).putExtra(STATUS_KEY, false)
        )
        id = null
        deviceIndexes.clear()
        observable.reset()
        _status.tryEmit(Status.Default)
        Log.i(TAG, "scanner session stopped")
    }

    private companion object {
        const val TAG = "TandaScanner"
        const val REPLAY = 1
        const val CLOSE_ATTEMPTS = 3
        const val CLOSE_RETRY_DELAY_MS = 100L
        const val CAPTURE_CANCEL_DELAY_MS = 100L
        const val STATUS_KEY = "enable"
        const val ACTION_FINGER_CONFIG = "mtk.intent.ACTION_FINGER_CONFIG"
    }
}
