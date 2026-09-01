package com.tanda.account.data.repository

import com.tanda.account.data.api.authentication.AuthenticationApi
import com.tanda.account.data.model.auth.Authentication
import com.tanda.account.domain.repository.TokenRepository
import com.tanda.core.persistence.preference.SharedPreference
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single
import kotlin.reflect.typeOf

@Single
class TokenRepositoryDelegate(
    private val preference: SharedPreference
) : TokenRepository, AuthenticationApi.Listener {
    override fun observe(): Flow<String?> {
        return preference.observe(TOKEN_KEY, typeOf<String>())
    }

    override fun get(): String? {
        return preference.get(TOKEN_KEY, typeOf<String>())
    }

    override fun onAuthenticate(authentication: Authentication?) {
        if (authentication != null) {
            preference.set(
                TOKEN_KEY,
                authentication.token
            )
        } else {
            preference.remove(TOKEN_KEY)
        }
    }

    override fun clear() {
        preference.remove(TOKEN_KEY)
    }

    private companion object {
        const val TOKEN_KEY = "token"
    }
}
