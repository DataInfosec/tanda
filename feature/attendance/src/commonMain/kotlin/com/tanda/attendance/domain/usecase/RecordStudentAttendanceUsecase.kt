package com.tanda.attendance.domain.usecase

import com.tanda.attendance.domain.model.AttendanceAction
import com.tanda.attendance.domain.model.AttendanceRecord
import com.tanda.attendance.domain.repository.DevicePointRepository
import com.tanda.attendance.exception.AttendancePointNotFoundException
import com.tanda.attendance.exception.StudentNotRegisteredAtPointException
import com.tanda.biometrics.domain.model.Image
import com.tanda.biometrics.domain.usecase.ClockActivityUsecase
import com.tanda.biometrics.domain.usecase.IdentificationUsecase
import com.tanda.core.common.usecase.SuspendWithArgsUseCase
import org.koin.core.annotation.Factory

@Factory
class RecordStudentAttendanceUsecase(
    private val repository: DevicePointRepository,
    private val identificationUsecase: IdentificationUsecase,
    private val clockActivityUsecase: ClockActivityUsecase,
) : SuspendWithArgsUseCase<RecordStudentAttendanceUsecase.Argument, AttendanceRecord> {
    override suspend fun invoke(args: Argument): AttendanceRecord {
        val point = repository.get(
            pointId = args.pointId,
            subjectType = STUDENT_SUBJECT_TYPE,
        ) ?: throw AttendancePointNotFoundException()

        val capture = identificationUsecase(args.image)
        if (capture.id !in point.memberSubjectIds) {
            throw StudentNotRegisteredAtPointException()
        }

        val submissionId = clockActivityUsecase(
            ClockActivityUsecase.Argument(
                pointId = point.id,
                capture = capture,
                attendanceType = args.action.toBiometricType(),
            )
        )
        return AttendanceRecord(
            submissionId = submissionId,
            subjectId = capture.id,
            pointId = point.id,
            action = args.action,
        )
    }

    data class Argument(
        val pointId: String,
        val action: AttendanceAction,
        val image: Image,
    )

    private companion object {
        const val STUDENT_SUBJECT_TYPE = "student"
    }
}
