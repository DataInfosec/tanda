package com.tanda.account.data.api.authentication

import com.tanda.account.data.model.auth.Authentication

interface AuthenticationApi {
    suspend fun login(login: String, password: String): Authentication

    suspend fun logout()

    interface Listener {
        fun onAuthenticate(authentication: Authentication?)
    }
}