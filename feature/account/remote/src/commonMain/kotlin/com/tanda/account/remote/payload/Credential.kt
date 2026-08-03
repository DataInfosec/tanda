package com.tanda.account.remote.payload

import kotlinx.serialization.Serializable

@Serializable
data class Credential(
    val login: String,
    val password: String
)
