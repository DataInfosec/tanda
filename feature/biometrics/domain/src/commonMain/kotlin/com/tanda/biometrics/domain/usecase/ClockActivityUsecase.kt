package com.tanda.biometrics.domain.usecase

import com.tanda.biometrics.domain.model.AttendanceType
import com.tanda.biometrics.domain.model.Capture
import com.tanda.biometrics.domain.repository.FingerprintRepository
import com.tanda.core.common.usecase.SuspendWithArgsUseCase
import org.koin.core.annotation.Factory

@Factory
class ClockActivityUsecase(
    private val repository: FingerprintRepository,
) : SuspendWithArgsUseCase<ClockActivityUsecase.Argument, String> {
    override suspend fun invoke(args: Argument): String {
        return repository.clockActivities(
            pointID = args.pointId,
            captureEvidence = args.capture,
            mobileAttendanceType = args.attendanceType,
        )
    }

    data class Argument(
        val pointId: String,
        val capture: Capture,
        val attendanceType: AttendanceType,
    )
}
