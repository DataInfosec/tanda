package com.tanda.biometrics.remote

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module
@ComponentScan(value = [ "com.tanda.biometrics.remote.api" ])
class RemoteModule
