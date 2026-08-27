package com.tanda.module

import com.tanda.core.persistence.PersistenceModule
import com.tanda.preference.PreferenceModule
import org.koin.core.annotation.Module

@Module(
    includes = [
        CoreModule::class,
        PreferenceModule::class,
        PersistenceModule::class,
        NetworkModule::class,
        AccountModule::class,
        BiometricsModule::class,
    ]
)
object TandaModule
