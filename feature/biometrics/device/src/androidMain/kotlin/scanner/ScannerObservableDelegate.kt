package scanner

import com.integratedbiometrics.ibscanultimate.IBScanDevice
import com.integratedbiometrics.ibscanultimate.IBScanDeviceListener
import com.integratedbiometrics.ibscanultimate.IBScanException
import com.tanda.biometrics.domain.exception.DeviceLostException
import com.tanda.biometrics.domain.model.Image
import com.tanda.biometrics.domain.model.Mode
import com.tanda.biometrics.domain.model.State
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class ScannerObservableDelegate : ScannerObservable, IBScanDeviceListener {
    private val _state = MutableSharedFlow<State>(replay = REPLAY)

    private val _mode = MutableSharedFlow<Mode>(replay = REPLAY)

    override val state: Flow<State> get() = _state

    override val mode: Flow<Mode> get() = _mode

    init {
        _state.tryEmit(State.Default)
        _mode.tryEmit(Mode.Default)
    }

    override fun deviceCommunicationBroken(device: IBScanDevice?) {
        closeWithRetry(device)
        _mode.tryEmit(Mode.Error(DeviceLostException()))
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
    ) {}

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
        _mode.tryEmit(Mode.Process(imageType.name))
    }

    override fun deviceAcquisitionCompleted(
        device: IBScanDevice?,
        imageType: IBScanDevice.ImageType
    ) {
        _mode.tryEmit(Mode.Acquired(imageType.name))
    }

    override fun deviceImageResultAvailable(
        device: IBScanDevice?,
        image: IBScanDevice.ImageData,
        imageType: IBScanDevice.ImageType?,
        splitImageArray: Array<out IBScanDevice.ImageData?>?
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
        _mode.tryEmit(Mode.Platen(platenState.name))
    }

    override fun deviceWarningReceived(
        device: IBScanDevice?,
        warning: IBScanException?
    ) {}

    override fun devicePressedKeyButtons(
        device: IBScanDevice?,
        pressedKeyButtons: Int
    ) {}

    override fun reset() {
        _state.tryEmit(State.Default)
    }

    private companion object {
        const val REPLAY = 1
    }
}
