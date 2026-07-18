package com.tanda.module

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module(
    includes = [
        CoreModule::class,
        ScannerModule::class,
    ]
)
@ComponentScan("com.tanda.interactor")
object AppModule
