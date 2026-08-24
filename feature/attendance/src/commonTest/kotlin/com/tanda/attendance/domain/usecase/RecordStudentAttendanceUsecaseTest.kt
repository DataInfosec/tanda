package com.tanda.attendance.domain.usecase

import com.tanda.attendance.domain.model.AttendanceAction
import com.tanda.attendance.domain.model.AttendancePoint
import com.tanda.attendance.domain.repository.DevicePointRepository
import com.tanda.attendance.exception.StudentNotRegisteredAtPointException
import com.tanda.biometrics.domain.model.AttendanceType
import com.tanda.biometrics.domain.model.Capture
import com.tanda.biometrics.domain.model.Image
import com.tanda.biometrics.domain.repository.FingerprintRepository
import com.tanda.biometrics.domain.usecase.ClockActivityUsecase
import com.tanda.biometrics.domain.usecase.IdentificationUsecase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RecordStudentAttendanceUsecaseTest {
    @Test
    fun registeredStudentQueuesSelectedClockActivity() = runTest {
        val point = attendancePoint(memberSubjectIds = listOf(SUBJECT_ID))
        val fingerprintRepository = FakeFingerprintRepository(capture = capture(SUBJECT_ID))
        val usecase = createUsecase(point, fingerprintRepository)

        val result = usecase(
            RecordStudentAttendanceUsecase.Argument(
                pointId = point.id,
                action = AttendanceAction.CLOCK_IN,
                image = image(),
            )
        )

        assertEquals(SUBMISSION_ID, result.submissionId)
        assertEquals(point.id, fingerprintRepository.clockPointId)
        assertEquals(AttendanceType.CLOCK_IN, fingerprintRepository.clockType)
        assertEquals(1, fingerprintRepository.clockCount)
    }

    @Test
    fun unregisteredStudentShowsPointErrorWithoutClocking() = runTest {
        val point = attendancePoint(memberSubjectIds = listOf("another-subject"))
        val fingerprintRepository = FakeFingerprintRepository(capture = capture(SUBJECT_ID))
        val usecase = createUsecase(point, fingerprintRepository)

        val error = assertFailsWith<StudentNotRegisteredAtPointException> {
            usecase(
                RecordStudentAttendanceUsecase.Argument(
                    pointId = point.id,
                    action = AttendanceAction.CLOCK_OUT,
                    image = image(),
                )
            )
        }

        assertEquals(
            "Student is not registered for attendance at this point",
            error.message,
        )
        assertEquals(0, fingerprintRepository.clockCount)
    }

    private fun createUsecase(
        point: AttendancePoint,
        fingerprintRepository: FakeFingerprintRepository,
    ): RecordStudentAttendanceUsecase {
        return RecordStudentAttendanceUsecase(
            repository = FakeDevicePointRepository(point),
            identificationUsecase = IdentificationUsecase(fingerprintRepository),
            clockActivityUsecase = ClockActivityUsecase(fingerprintRepository),
        )
    }

    private fun attendancePoint(memberSubjectIds: List<String>): AttendancePoint {
        return AttendancePoint(
            id = POINT_ID,
            code = "PNT-ATTENDANCE",
            name = "Main Gate",
            pointPopulationId = "population-id",
            memberSubjectIds = memberSubjectIds,
        )
    }

    private fun capture(subjectId: String): Capture {
        return Capture(
            id = subjectId,
            score = 98f,
            recordId = "record-id",
            galleryId = "gallery-id",
            galleryRevision = 1u,
            modality = "fingerprint",
            verificationScore = 97f,
            provisionalEnrollmentSubmissionId = null,
            evidenceToken = "evidence-token",
        )
    }

    private fun image(): Image = Image(
        width = 2,
        height = 2,
        data = byteArrayOf(1, 2, 3, 4),
    )

    private class FakeDevicePointRepository(
        private val point: AttendancePoint,
    ) : DevicePointRepository {
        override fun observe(subjectType: String): Flow<List<AttendancePoint>> = flowOf(listOf(point))

        override suspend fun refresh(subjectType: String) = Unit

        override suspend fun get(pointId: String, subjectType: String): AttendancePoint? {
            return point.takeIf { it.id == pointId }
        }
    }

    private class FakeFingerprintRepository(
        private val capture: Capture,
    ) : FingerprintRepository {
        var clockPointId: String? = null
        var clockType: AttendanceType? = null
        var clockCount: Int = 0

        override suspend fun identify(image: Image): Capture = capture

        override suspend fun enroll(id: String, images: List<Image>) = Unit

        override suspend fun clockActivities(
            pointID: String,
            captureEvidence: Capture,
            mobileAttendanceType: AttendanceType,
        ): String {
            clockPointId = pointID
            clockType = mobileAttendanceType
            clockCount += 1
            return SUBMISSION_ID
        }

        override suspend fun synchronize() = Unit
    }

    private companion object {
        const val POINT_ID = "point-id"
        const val SUBJECT_ID = "student-id"
        const val SUBMISSION_ID = "submission-id"
    }
}
