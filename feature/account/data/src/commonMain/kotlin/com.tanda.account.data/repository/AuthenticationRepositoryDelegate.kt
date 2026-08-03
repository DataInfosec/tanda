package com.tanda.account.data.repository

import com.tanda.account.data.api.AuthenticationApi
import com.tanda.account.domain.repository.AuthenticationRepository
import org.koin.core.annotation.Factory

@Factory
class AuthenticationRepositoryDelegate(
    private val api: AuthenticationApi,
    private val listener: AuthenticationApi.Listener
) : AuthenticationRepository {
    override suspend fun login(login: String, password: String): String {
        val authentication = api.login(login, password)
        listener.onAuthenticate(authentication)
        return authentication.token
    }

    override suspend fun logout() {
        api.logout()
        listener.onAuthenticate(null)
    }
}
