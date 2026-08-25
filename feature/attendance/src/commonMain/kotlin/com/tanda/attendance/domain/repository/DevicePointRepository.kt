package com.tanda.attendance.domain.repository

import com.tanda.attendance.domain.model.AttendancePoint
import kotlinx.coroutines.flow.Flow

interface DevicePointRepository {
    fun observe(subjectType: String): Flow<List<AttendancePoint>>

    suspend fun refresh(subjectType: String)

    suspend fun get(pointId: String, subjectType: String): AttendancePoint?
}
