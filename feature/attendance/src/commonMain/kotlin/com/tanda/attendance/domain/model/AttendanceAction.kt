package com.tanda.attendance.domain.model

import com.tanda.biometrics.domain.model.AttendanceType

enum class AttendanceAction {
    CLOCK_IN,
    CLOCK_OUT;

    val label: String
        get() = when (this) {
            CLOCK_IN -> "Clock-in"
            CLOCK_OUT -> "Clock-out"
        }

    fun toBiometricType(): AttendanceType {
        return when (this) {
            CLOCK_IN -> AttendanceType.CLOCK_IN
            CLOCK_OUT -> AttendanceType.CLOCK_OUT
        }
    }
}
