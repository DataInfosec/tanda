package com.tanda.biometrics.domain

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module
@ComponentScan(
    value = [
        "com.tanda.biometrics.domain.usecase",
        "com.tanda.biometrics.domain.session",
    ]
)
class DomainModule
