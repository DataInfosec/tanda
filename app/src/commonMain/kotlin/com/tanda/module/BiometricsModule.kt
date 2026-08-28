package com.tanda.module

import com.datainfosec.biometric.MobileDeviceTokenProvider
import com.tanda.BuildConstants
import com.tanda.biometrics.data.DataModule
import com.tanda.biometrics.device.DeviceModule
import com.tanda.biometrics.domain.DomainModule
import com.tanda.biometrics.remote.RemoteModule
import com.tanda.biometrics.verification.VerificationModule
import com.tanda.biometrics.verification.model.Credential
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

@Module(
    includes = [
        VerificationModule::class,
        DeviceModule::class,
        RemoteModule::class,
        DataModule::class,
        DomainModule::class
    ]
)
class BiometricsModule {
    @Single
    fun provideCredential(@Named("path") path: String): Credential {
        return Credential(
            id = BuildConstants.DEVICE_ID,
            url = BuildConstants.FINGERPRINT_URL,
            path = path,
        )
    }

    @Single
    fun provideTokenProvider(@Named("path") path: String): MobileDeviceTokenProvider {
        return object : MobileDeviceTokenProvider {
            override fun currentToken(): String {
                return "86MtYqYuMTjV-sTN7LVhQZ8fjocWCbPsqTGIl2uOYCo"
            }
        }
    }
}