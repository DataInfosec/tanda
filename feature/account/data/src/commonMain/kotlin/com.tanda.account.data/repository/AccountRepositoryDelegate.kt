package com.tanda.account.data.repository

import com.tanda.account.domain.model.Account
import com.tanda.account.domain.repository.AccountRepository
import org.koin.core.annotation.Factory

@Factory
class AccountRepositoryDelegate : AccountRepository {
    override suspend fun get(): Account {
        TODO("Not yet implemented")
    }
}
