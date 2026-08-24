package com.tanda.attendance.ui.checkin

import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import com.tanda.biometrics.ui.fingerprint.FingerprintViewModel
import com.tanda.core.ui.design.DesignStreamState
import org.koin.ext.getFullName

interface CheckinEvent {
    val viewModel: FingerprintViewModel

    val stream: State<DesignStreamState<Unit>>

    companion object {
        val LocalCheckinEvent = staticCompositionLocalOf<CheckinEvent> {
            error("${CheckinEvent::class.getFullName()} not provided")
        }
    }
}
