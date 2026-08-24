package com.tanda.attendance.domain.usecase

import com.tanda.attendance.domain.model.AttendanceAction
import com.tanda.attendance.domain.model.AttendanceOption
import com.tanda.attendance.domain.repository.DevicePointRepository
import com.tanda.core.common.usecase.ObservableUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Factory

@Factory
class ObserveStudentAttendancePointsUsecase(
    private val repository: DevicePointRepository,
) : ObservableUseCase<List<AttendanceOption>> {
    override fun invoke(): Flow<List<AttendanceOption>> {
        return repository.observe(STUDENT_SUBJECT_TYPE).map { points ->
            points.flatMap { point ->
                AttendanceAction.entries.map { action ->
                    AttendanceOption(point = point, action = action)
                }
            }
        }
    }

    private companion object {
        const val STUDENT_SUBJECT_TYPE = "student"
    }
}
