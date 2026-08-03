package com.tanda.account.data.api

import com.tanda.account.data.model.Authentication

interface AuthenticationApi {
    suspend fun login(login: String, password: String): Authentication

    suspend fun logout()

    interface Listener {
        fun onAuthenticate(authentication: Authentication?)
    }
}
