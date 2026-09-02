package com.tanda.account.domain

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module
@ComponentScan(value = [
    "com.tanda.account.domain.usecase.account",
    "com.tanda.account.domain.usecase.auth",
    "com.tanda.account.domain.usecase.device",
])
class DomainModule
