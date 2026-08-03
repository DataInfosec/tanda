package com.tanda.account.remote.api

import com.tanda.account.data.api.AccountApi
import com.tanda.account.domain.model.Account
import com.tanda.core.remote.client.NetworkClient
import io.ktor.client.HttpClient
import org.koin.core.annotation.Factory

@Factory
class AccountApiDelegate(client: HttpClient) : NetworkClient(client), AccountApi {
    override suspend fun get(): Account {
        TODO("Not yet implemented")
    }
}
