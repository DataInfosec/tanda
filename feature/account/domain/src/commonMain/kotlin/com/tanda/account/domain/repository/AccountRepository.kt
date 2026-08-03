package com.tanda.account.domain.repository

import com.tanda.account.domain.model.Account

interface AccountRepository {
    suspend fun get(): Account
}
