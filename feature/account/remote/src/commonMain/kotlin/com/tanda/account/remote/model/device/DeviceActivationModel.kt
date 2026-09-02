package com.tanda.account.remote.model.device

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceActivationModel(
    val instance: DeviceInstanceModel,
    @SerialName("bearer_token")
    val bearerToken: String,
    @SerialName("gallery_sync_url")
    val gallerySyncUrl: String,
)