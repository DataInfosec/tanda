package com.tanda.account.data.repository

import com.tanda.account.data.api.authentication.AuthenticationApi
import com.tanda.account.domain.repository.AuthenticationRepository
import org.koin.core.annotation.Single

@Single
class AuthenticationRepositoryDelegate(
    private val api: AuthenticationApi,
    private val listeners: Listeners
) : AuthenticationRepository {
    override suspend fun login(login: String, password: String): String {
        val authentication = api.login(login, password)
        listeners.value.forEach { it.onAuthenticate(authentication) }
        return authentication.token
    }

    override suspend fun logout() {
        api.logout()
        listeners.value.forEach { it.onAuthenticate(null) }
    }

    data class Listeners(val value: List<AuthenticationApi.Listener>)
}
