package com.tanda.account.data.repository

import com.tanda.account.data.api.AuthenticationApi
import com.tanda.account.data.model.Authentication
import com.tanda.account.domain.repository.TokenRepository
import com.tanda.core.persistence.usecase.ClearStringUsecase
import com.tanda.core.persistence.usecase.GetStringUsecase
import com.tanda.core.persistence.usecase.ObservableStringUsecase
import com.tanda.core.persistence.usecase.UpdateStringUsecase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.koin.core.annotation.Singleton

@Singleton
class TokenRepositoryDelegate(
    private val getStringUsecase: GetStringUsecase,
    private val updateStringUsecase: UpdateStringUsecase,
    private val observableStringUsecase: ObservableStringUsecase,
    private val clearStringUsecase: ClearStringUsecase
) : TokenRepository, AuthenticationApi.Listener {
    private val expiration = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1
    )

    override fun observe(): Flow<String?> {
        return observableStringUsecase(TOKEN_KEY)
    }

    override fun observeExpiration(): Flow<Unit> {
        return expiration
    }

    override fun get(): String? {
        return getStringUsecase(TOKEN_KEY)
    }

    override fun onAuthenticate(authentication: Authentication?) {
        if (authentication != null) {
            updateStringUsecase(
                UpdateStringUsecase.Argument(
                    key = TOKEN_KEY,
                    value = authentication.token
                )
            )
        } else {
            clearStringUsecase(TOKEN_KEY)
        }
    }

    override fun clear() {
        clearStringUsecase(TOKEN_KEY)
    }

    override fun expire() {
        clear()
        expiration.tryEmit(Unit)
    }

    private companion object {
        const val TOKEN_KEY = "token"
    }
}
