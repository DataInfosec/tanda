package com.tanda.attendance.module

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module(
    includes = [
        BiometricsModule::class,
    ]
)
@ComponentScan(value = [
    "com.tanda.attendance.interactor",
    "com.tanda.attendance.remote",
])
class AttendanceModule
