package com.tanda.biometrics.domain.model

data class DeviceConfiguration(
    val deviceInstanceId: String,
    val fingerprintToken: String,
)
