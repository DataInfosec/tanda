package com.tanda.module

import com.tanda.biometrics.data.DataModule
import com.tanda.biometrics.device.DeviceModule
import com.tanda.biometrics.domain.DomainModule
import com.tanda.biometrics.verification.VerificationModule
import org.koin.core.annotation.Module

@Module(
    includes = [
        VerificationModule::class,
        DeviceModule::class,
        DataModule::class,
        DomainModule::class
    ]
)
class ScannerModule
