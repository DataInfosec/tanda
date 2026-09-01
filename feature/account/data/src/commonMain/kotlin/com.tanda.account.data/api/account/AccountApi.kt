package com.tanda.account.data.api.account

import com.tanda.account.domain.model.Account

interface AccountApi {
    suspend fun get(): Account
}