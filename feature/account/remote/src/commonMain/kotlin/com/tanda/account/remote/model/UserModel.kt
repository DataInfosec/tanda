package com.tanda.account.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserModel(
    val id: String,
    @SerialName("full_name")
    val fullName: String,
    val email: String,
    val username: String,
    val category: String,
    @SerialName("access_mode")
    val accessMode: String,
    val status: String,
    val grants: List<GrantModel>,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String
)
