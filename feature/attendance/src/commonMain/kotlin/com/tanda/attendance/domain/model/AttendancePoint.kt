package com.tanda.attendance.domain.model

data class AttendancePoint(
    val id: String,
    val code: String,
    val name: String,
    val pointPopulationId: String,
    val memberSubjectIds: List<String>,
)

data class AttendanceOption(
    val point: AttendancePoint,
    val action: AttendanceAction,
) {
    val label: String
        get() = "${point.name} ${action.label}"
}
