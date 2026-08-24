package com.tanda.attendance.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DevicePointResponse(
    @SerialName("device_instance_id")
    val deviceInstanceId: String,
    @SerialName("logical_device_id")
    val logicalDeviceId: String,
    @SerialName("organization_id")
    val organizationId: String,
    @SerialName("site_id")
    val siteId: String,
    @SerialName("gallery_population_id")
    val galleryPopulationId: String,
    @SerialName("subject_type")
    val subjectType: String,
    val points: List<AttendancePointModel>,
)

@Serializable
data class AttendancePointModel(
    val id: String,
    val code: String,
    val name: String,
    @SerialName("point_population_id")
    val pointPopulationId: String,
    @SerialName("member_subject_ids")
    val memberSubjectIds: List<String>,
)
