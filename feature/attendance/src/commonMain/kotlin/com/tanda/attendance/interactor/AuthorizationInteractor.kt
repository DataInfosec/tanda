package com.tanda.attendance.interactor

import com.datainfosec.biometric.MobileDeviceTokenProvider
import com.tanda.account.domain.repository.TokenRepository
import com.tanda.attendance.exception.AuthorizationException
import org.koin.core.annotation.Factory

@Factory
class AuthorizationInteractor(
    private val tokenRepository: TokenRepository
) : MobileDeviceTokenProvider {
    override fun currentToken(): String {
        return tokenRepository.get() ?: throw AuthorizationException()
    }
}
