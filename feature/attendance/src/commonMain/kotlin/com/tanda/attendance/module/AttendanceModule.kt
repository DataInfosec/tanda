package com.tanda.attendance.module

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module(
    includes = [
        BiometricsModule::class,
    ]
)
@ComponentScan("com.tanda.attendance.interactor")
class AttendanceModule
