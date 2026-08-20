package com.tanda.attendance.module

import com.tanda.BuildConstants
import com.tanda.biometrics.data.DataModule
import com.tanda.biometrics.device.DeviceModule
import com.tanda.biometrics.domain.DomainModule
import com.tanda.biometrics.domain.repository.DeviceConfigurationRepository
import com.tanda.biometrics.verification.VerificationModule
import com.tanda.biometrics.verification.model.Credential
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

@Module(
    includes = [
        VerificationModule::class,
        DeviceModule::class,
        DataModule::class,
        DomainModule::class
    ]
)
class BiometricsModule {
    @Single
    fun provideCredential(
        @Named("path") path: String,
        deviceConfigurationRepository: DeviceConfigurationRepository,
    ): Credential {
        val deviceConfiguration = requireNotNull(deviceConfigurationRepository.get()) {
            "Device configuration is required before biometric operations"
        }
        return Credential(
            id = deviceConfiguration.deviceInstanceId,
            url = BuildConstants.FINGERPRINT_URL,
            path = path,
        )
    }
}
