package com.tanda.account.data

import com.tanda.account.data.repository.AccountRepositoryDelegate
import com.tanda.account.data.repository.AuthenticationRepositoryDelegate
import com.tanda.account.data.repository.TokenRepositoryDelegate
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Singleton

@Module
@ComponentScan(value = [
    "com.tanda.account.data.interactor",
    "com.tanda.account.data.repository"
])
class DataModule {
    @Singleton
    fun authenticationListeners(
        accountRepository: AccountRepositoryDelegate,
        tokenRepository: TokenRepositoryDelegate,
    ): AuthenticationRepositoryDelegate.Listeners =
        AuthenticationRepositoryDelegate.Listeners(listOf(
            accountRepository,
            tokenRepository
        ))
}
