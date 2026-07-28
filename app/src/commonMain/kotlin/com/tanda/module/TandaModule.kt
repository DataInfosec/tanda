package com.tanda.module

import com.tanda.attendance.module.AttendanceModule
import com.tanda.preference.PreferenceModule
import org.koin.core.annotation.Module

@Module(
    includes = [
        CoreModule::class,
        PreferenceModule::class,
        AttendanceModule::class,
    ]
)
object TandaModule
