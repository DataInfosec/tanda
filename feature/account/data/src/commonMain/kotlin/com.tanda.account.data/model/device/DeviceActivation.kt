package com.tanda.account.data.model.device

data class DeviceActivation(
    val deviceId: String,
    val deviceToken: String,
    val gallerySyncUrl: String,
)
