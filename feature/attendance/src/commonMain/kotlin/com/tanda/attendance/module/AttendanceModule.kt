package com.tanda.attendance.module

import org.koin.core.annotation.Module

@Module(
    includes = [
        BiometricsModule::class,
    ]
)
class AttendanceModule
