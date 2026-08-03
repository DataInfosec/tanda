package com.tanda.core.persistence

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module
@ComponentScan(value = [
    "com.tanda.core.persistence.usecase",
])
class PersistenceModule
