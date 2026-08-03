package com.tanda.account.remote.api

import com.tanda.account.data.api.AuthenticationApi
import com.tanda.account.data.model.Authentication
import com.tanda.account.remote.mapper.mapToDomain
import com.tanda.account.remote.model.AuthenticationModel
import com.tanda.account.remote.payload.Credential
import com.tanda.core.remote.client.NetworkClient
import com.tanda.core.remote.exception.NetworkException
import io.ktor.client.HttpClient
import org.koin.core.annotation.Factory

@Factory
class AuthenticationApiDelegate(client: HttpClient) : NetworkClient(client), AuthenticationApi {
    override suspend fun login(
        login: String,
        password: String
    ): Authentication {
        val credential = Credential(login, password)
        val response = post<AuthenticationModel, Credential>(
            url = LOGIN_PATH,
            body = credential
        )
        if (response.isFailure) {
            val error = response.exceptionOrNull()
            throw NetworkException(error?.message, error)
        }
        return response.getOrThrow().mapToDomain()
    }

    override suspend fun logout() {
        TODO("Not yet implemented")
    }

    private companion object {
        const val LOGIN_PATH = "/v1/auth/admin/login"
    }
}
