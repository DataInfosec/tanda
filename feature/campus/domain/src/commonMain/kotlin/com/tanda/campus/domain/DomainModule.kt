package com.tanda.campus.domain

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module
@ComponentScan(value = [
    "com.tanda.campus.domain.usecase",
])
class DomainModule
