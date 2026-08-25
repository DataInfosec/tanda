package com.tanda.attendance.data.mapper

import com.tanda.attendance.data.model.AttendancePointModel
import com.tanda.attendance.domain.model.AttendancePoint

fun AttendancePointModel.mapToDomain(): AttendancePoint {
    return AttendancePoint(
        id = id,
        code = code,
        name = name,
        pointPopulationId = pointPopulationId,
        memberSubjectIds = memberSubjectIds,
    )
}
