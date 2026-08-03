package com.tanda.account.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthenticationModel(
    val token: String,
    val user: UserModel,
    @SerialName("idle_expires_at")
    val idleExpiresAt: String,
    @SerialName("absolute_expires_at")
    val absoluteExpiresAt: String
)
