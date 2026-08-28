package com.tanda.account.domain.usecase

import com.tanda.account.domain.model.Account
import com.tanda.account.domain.repository.AccountRepository
import com.tanda.core.common.usecase.SuspendUseCase
import org.koin.core.annotation.Factory

@Factory
class AccountUsecase(
    private val repository: AccountRepository
) : SuspendUseCase<Account> {
    override suspend fun invoke(): Account {
        return repository.get()
    }
}
