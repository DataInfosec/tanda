package com.tanda.module

import com.tanda.preference.PreferenceModule
import org.koin.core.annotation.Module

@Module(
    includes = [
        CoreModule::class,
        PreferenceModule::class,
        ScannerModule::class,
    ]
)
object TandaModule
