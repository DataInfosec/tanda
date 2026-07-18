package com.tanda.module

import com.tanda.biometrics.data.DataModule
import com.tanda.biometrics.device.DeviceModule
import com.tanda.biometrics.domain.DomainModule
import org.koin.core.annotation.Module

@Module(
    includes = [
        DeviceModule::class,
        DataModule::class,
        DomainModule::class
    ]
)
object ScannerModule
