package com.tanda.account.remote

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module
@ComponentScan(value = [ "com.tanda.account.remote.api" ])
class RemoteModule
