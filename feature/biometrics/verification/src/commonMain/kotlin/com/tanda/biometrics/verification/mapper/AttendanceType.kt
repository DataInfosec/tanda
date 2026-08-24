package com.tanda.biometrics.verification.mapper

import com.datainfosec.biometric.MobileAttendanceEventType
import com.tanda.biometrics.domain.model.AttendanceType


fun AttendanceType.mapToData(): MobileAttendanceEventType {
    return when (this) {
        AttendanceType.CLOCK_IN -> MobileAttendanceEventType.CLOCK_IN
        AttendanceType.CLOCK_OUT -> MobileAttendanceEventType.CLOCK_OUT
    }
}
