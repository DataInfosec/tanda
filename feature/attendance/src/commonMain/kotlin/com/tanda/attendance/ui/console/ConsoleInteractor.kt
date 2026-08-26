package com.tanda.attendance.ui.console

import androidx.compose.runtime.State
import com.tanda.attendance.ui.checkin.CheckinEvent
import com.tanda.attendance.ui.enrollment.EnrollmentEvent
import com.tanda.biometrics.ui.fingerprint.FingerprintViewModel
import com.tanda.core.ui.design.DesignStreamState

class ConsoleInteractor(
    override val viewModel: FingerprintViewModel,
    override val stream: State<DesignStreamState<Unit>>
) : EnrollmentEvent, CheckinEvent
