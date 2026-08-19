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
        return "f9F8Wm3dyLRR-xgv2cuuKa5dxXJ4SVm9bSk8LJctTCw"/* "86MtYqYuMTjV-sTN7LVhQZ8fjocWCbPsqTGIl2uOYCo"*/
    }
}
