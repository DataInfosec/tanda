package com.tanda.module

import com.tanda.preference.PreferenceModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module(
    includes = [
        CoreModule::class,
        PreferenceModule::class,
        ScannerModule::class,
    ]
)
@ComponentScan("com.tanda.interactor")
object AppModule
