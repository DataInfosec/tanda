package com.tanda.attendance.interactor

import com.datainfosec.biometric.MobileDeviceTokenProvider
import com.tanda.BuildConstants
import com.tanda.attendance.exception.AuthorizationException
import org.koin.core.annotation.Factory

@Factory
class AuthorizationInteractor : MobileDeviceTokenProvider {
    override fun currentToken(): String {
        return BuildConstants.DEVICE_TOKEN.takeIf { it.isNotBlank() }
            ?: throw AuthorizationException()
    }
}
