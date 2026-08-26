package com.tanda.attendance.interactor

import com.datainfosec.biometric.MobileDeviceTokenProvider
import com.tanda.account.domain.repository.TokenRepository
import org.koin.core.annotation.Factory

@Factory
class AuthorizationInteractor(
    private val tokenRepository: TokenRepository
) : MobileDeviceTokenProvider {
    override fun currentToken(): String {
        return "86MtYqYuMTjV-sTN7LVhQZ8fjocWCbPsqTGIl2uOYCo"
    }
}
