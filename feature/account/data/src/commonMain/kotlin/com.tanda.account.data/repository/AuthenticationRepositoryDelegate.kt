package com.tanda.account.data.repository

import com.tanda.account.data.api.AuthenticationApi
import com.tanda.account.domain.repository.AuthenticationRepository
import com.tanda.core.persistence.usecase.UpdateStringUsecase
import org.koin.core.annotation.Factory

@Factory
class AuthenticationRepositoryDelegate(
    private val api: AuthenticationApi,
    private val listener: AuthenticationApi.Listener,
    private val updateStringUsecase: UpdateStringUsecase,
) : AuthenticationRepository {
    override suspend fun login(login: String, password: String): String {
        val authentication = api.login(login, password)
        listener.onAuthenticate(authentication)
        updateStringUsecase(
            UpdateStringUsecase.Argument(
                USER_KEY, authentication.account.name
            )
        )
        return authentication.token
    }

    override suspend fun logout() {
        api.logout()
        listener.onAuthenticate(null)
    }

    companion object{
        const val USER_KEY = "user"
    }
}
