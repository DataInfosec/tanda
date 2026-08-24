package com.tanda.attendance.domain.model

data class AttendanceRecord(
    val submissionId: String,
    val subjectId: String,
    val pointId: String,
    val action: AttendanceAction,
)
