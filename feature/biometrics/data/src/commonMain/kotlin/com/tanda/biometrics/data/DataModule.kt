package com.tanda.biometrics.data

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module
@ComponentScan(
    value = [
        "com.tanda.biometrics.data.api",
        "com.tanda.biometrics.data.repository"
    ]
)
class DataModule
