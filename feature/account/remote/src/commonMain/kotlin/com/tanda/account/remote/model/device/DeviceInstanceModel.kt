package com.tanda.account.remote.model.device

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceInstanceModel(
    val id: String,
    @SerialName("logical_device_id")
    val logicalDeviceId: String,
    val status: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
)