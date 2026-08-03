package com.tanda.account.domain.repository

interface AuthenticationRepository {
    suspend fun login(login: String, password: String): String

    suspend fun logout()
}
