package com.tanda.attendance.interactor

import com.datainfosec.biometric.MobileDeviceTokenProvider
import com.tanda.biometrics.domain.repository.DeviceConfigurationRepository
import org.koin.core.annotation.Factory

@Factory
class AuthorizationInteractor(
    private val deviceConfigurationRepository: DeviceConfigurationRepository,
) : MobileDeviceTokenProvider {
    override fun currentToken(): String {
        val deviceToken =deviceConfigurationRepository.get()?.fingerprintToken.orEmpty()
        println("deviceToken: $deviceToken")
        return deviceToken
    }
}
