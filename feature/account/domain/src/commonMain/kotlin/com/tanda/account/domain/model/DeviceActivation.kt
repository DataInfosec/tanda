package com.tanda.account.domain.model

data class DeviceActivation(
    val deviceId: String,
    val deviceToken: String,
    val gallerySyncUrl: String,
)
