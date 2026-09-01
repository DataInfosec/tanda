package com.tanda.account.data.model.auth

import com.tanda.account.domain.model.Account

data class Authentication(
    val token: String,
    val account: Account
)