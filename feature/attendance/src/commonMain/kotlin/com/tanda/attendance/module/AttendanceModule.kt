package com.tanda.attendance.module

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module(
    includes = [
        BiometricsModule::class,
    ]
)
@ComponentScan(
    value = [
        "com.tanda.attendance.data",
        "com.tanda.attendance.domain.usecase",
        "com.tanda.attendance.interactor",
    ]
)
class AttendanceModule
