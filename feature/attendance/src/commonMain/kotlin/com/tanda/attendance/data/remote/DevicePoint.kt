package com.tanda.attendance.data.remote

import com.tanda.attendance.data.model.DevicePointResponse

interface DevicePoint {
    suspend fun get(): DevicePointResponse
}
