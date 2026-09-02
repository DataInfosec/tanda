package com.tanda.account.data.model.account

import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val username: String,
    val email: String,
)