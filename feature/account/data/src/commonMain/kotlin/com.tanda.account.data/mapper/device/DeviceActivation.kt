package com.tanda.account.data.mapper.device

import com.tanda.account.domain.model.DeviceActivation
import com.tanda.account.data.model.device.DeviceActivation as DeviceActivationModel

fun DeviceActivationModel.mapToDomain(): DeviceActivation {
    return DeviceActivation(
        deviceId = deviceId,
        deviceToken = deviceToken,
        gallerySyncUrl = gallerySyncUrl
    )
}
