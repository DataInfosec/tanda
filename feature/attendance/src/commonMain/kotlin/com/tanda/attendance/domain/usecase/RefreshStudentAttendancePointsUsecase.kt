package com.tanda.attendance.domain.usecase

import com.tanda.attendance.domain.repository.DevicePointRepository
import com.tanda.core.common.usecase.SuspendUseCase
import org.koin.core.annotation.Factory

@Factory
class RefreshStudentAttendancePointsUsecase(
    private val repository: DevicePointRepository,
) : SuspendUseCase<Unit> {
    override suspend fun invoke() {
        repository.refresh(STUDENT_SUBJECT_TYPE)
    }

    private companion object {
        const val STUDENT_SUBJECT_TYPE = "student"
    }
}
