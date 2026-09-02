package com.tanda.account.remote.payload

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceActivationPayload(
    @SerialName("activation_code")
    val activationCode: String,
)
