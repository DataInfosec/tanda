package com.tanda.account.data

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module
@ComponentScan(value = [
    "com.tanda.account.data.interactor",
    "com.tanda.account.data.repository"
])
class DataModule
