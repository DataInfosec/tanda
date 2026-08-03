package com.tanda.account.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GrantModel(
    val id: String,
    @SerialName("scope_kind")
    val scopeKind: String,
    @SerialName("organization_id")
    val organizationId: String,
    @SerialName("site_id")
    val siteId: String,
    @SerialName("access_mode")
    val accessMode: String,
    val capabilities: List<String>
)
