package com.tanda.campus.data

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module
@ComponentScan(value = [
    "com.tanda.campus.data.repository",
])
class DataModule
