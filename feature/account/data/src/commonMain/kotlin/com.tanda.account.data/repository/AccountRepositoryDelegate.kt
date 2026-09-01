package com.tanda.account.data.repository

import com.tanda.account.data.api.account.AccountApi
import com.tanda.account.data.api.authentication.AuthenticationApi
import com.tanda.account.data.mapper.account.mapFromDomain
import com.tanda.account.data.mapper.account.mapToDomain
import com.tanda.account.data.model.auth.Authentication
import com.tanda.account.data.model.account.Profile
import com.tanda.account.domain.model.Account
import com.tanda.account.domain.repository.AccountRepository
import com.tanda.core.persistence.preference.SharedPreference
import org.koin.core.annotation.Single
import kotlin.reflect.typeOf

@Single
class AccountRepositoryDelegate(
    private val api: AccountApi,
    private val preference: SharedPreference
) : AccountRepository, AuthenticationApi.Listener {
    override suspend fun get(): Account {
        return preference.get<Profile>(ACCOUNT_KEY, typeOf<Profile>())?.mapToDomain()
            ?: api.get().apply {
                preference.set(ACCOUNT_KEY, mapFromDomain())
            }
    }

    override fun onAuthenticate(authentication: Authentication?) {
        authentication?.let { preference.set(ACCOUNT_KEY, it.account.mapFromDomain()) }
            ?: preference.remove(ACCOUNT_KEY)
    }

    private companion object {
        const val ACCOUNT_KEY = "account"
    }
}
