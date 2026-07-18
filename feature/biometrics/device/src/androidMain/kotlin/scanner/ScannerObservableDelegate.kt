package scanner

import com.integratedbiometrics.ibscanultimate.IBScanDevice
import com.integratedbiometrics.ibscanultimate.IBScanDeviceListener
import com.integratedbiometrics.ibscanultimate.IBScanException
import com.tanda.biometrics.device.exception.DeviceLostException
import com.tanda.biometrics.domain.model.Image
import com.tanda.biometrics.domain.model.Event
import com.tanda.biometrics.domain.model.State
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class ScannerObservableDelegate : ScannerObservable, IBScanDeviceListener {
    private val _state = MutableSharedFlow<State>(replay = REPLAY)

    private val _event = MutableSharedFlow<Event>(replay = REPLAY)

    override val state: Flow<State> get() = _state

    override val event: Flow<Event> get() = _event

    init {
        _state.tryEmit(State.Default)
        _event.tryEmit(Event.Default)
    }

    override fun deviceCommunicationBroken(device: IBScanDevice?) {
        closeWithRetry(device)
        _event.tryEmit(Event.Error(DeviceLostException()))
    }

    private fun closeWithRetry(device: IBScanDevice?, attempts: Int = 3) {
        repeat(attempts) {
            try {
                device?.close()
                return
            } catch (exception: IBScanException) {
                if (exception.type != IBScanException.Type.RESOURCE_LOCKED) return
            }
        }
    }

    override fun deviceImagePreviewAvailable(
        device: IBScanDevice?,
        image: IBScanDevice.ImageData
    ) {
        _state.tryEmit(
            State.Capture(
            Image(
                width = image.width,
                height = image.height,
                data = image.buffer
            )
        ))
    }

    override fun deviceFingerCountChanged(
        device: IBScanDevice?,
        fingerState: IBScanDevice.FingerCountState?
    ) {}

    override fun deviceFingerQualityChanged(
        device: IBScanDevice?,
        fingerQualities: Array<out IBScanDevice.FingerQualityState?>?
    ) {}

    override fun deviceAcquisitionBegun(
        device: IBScanDevice?,
        imageType: IBScanDevice.ImageType
    ) {
        _event.tryEmit(Event.Process(imageType.name))
    }

    override fun deviceAcquisitionCompleted(
        device: IBScanDevice?,
        imageType: IBScanDevice.ImageType
    ) {
        _event.tryEmit(Event.Complete(imageType.name))
    }

    override fun deviceImageResultAvailable(
        device: IBScanDevice?,
        image: IBScanDevice.ImageData?,
        imageType: IBScanDevice.ImageType?,
        splitImageArray: Array<out IBScanDevice.ImageData?>?
    ) {}

    override fun deviceImageResultExtendedAvailable(
        device: IBScanDevice?,
        imageStatus: IBScanException?,
        image: IBScanDevice.ImageData?,
        imageType: IBScanDevice.ImageType?,
        detectedFingerCount: Int,
        segmentImageArray: Array<out IBScanDevice.ImageData?>?,
        segmentPositionArray: Array<out IBScanDevice.SegmentPosition?>?
    ) {}

    override fun devicePlatenStateChanged(
        device: IBScanDevice?,
        platenState: IBScanDevice.PlatenState
    ) {
        _event.tryEmit(Event.Ready(platenState.name))
    }

    override fun deviceWarningReceived(
        device: IBScanDevice?,
        warning: IBScanException?
    ) {}

    override fun devicePressedKeyButtons(
        device: IBScanDevice?,
        pressedKeyButtons: Int
    ) {}

    private companion object {
        const val REPLAY = 1
    }
}
