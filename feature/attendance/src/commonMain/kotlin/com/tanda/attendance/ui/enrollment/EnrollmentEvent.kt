package com.tanda.attendance.ui.enrollment

import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import com.tanda.biometrics.ui.fingerprint.FingerprintViewModel
import com.tanda.core.ui.design.DesignStreamState
import org.koin.ext.getFullName

interface EnrollmentEvent {
    val viewModel: FingerprintViewModel

    val stream: State<DesignStreamState<Unit>>

    companion object {
        val LocalEnrollmentEvent = staticCompositionLocalOf<EnrollmentEvent> {
            error("${EnrollmentEvent::class.getFullName()} not provided")
        }
    }
}
