package com.tanda.module

import com.tanda.account.data.DataModule
import com.tanda.account.domain.DomainModule
import com.tanda.account.remote.RemoteModule
import org.koin.core.annotation.Module

@Module(
    includes = [
        DomainModule::class,
        DataModule::class,
        RemoteModule::class
    ]
)
class AccountModule
