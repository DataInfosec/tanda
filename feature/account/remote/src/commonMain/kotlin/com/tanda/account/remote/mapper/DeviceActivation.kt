package com.tanda.account.remote.mapper

import com.tanda.account.remote.model.device.DeviceActivationModel
import com.tanda.account.data.model.device.DeviceActivation as DeviceActivationDataModel

fun DeviceActivationModel.mapToData(): DeviceActivationDataModel =
    DeviceActivationDataModel(
        deviceId = instance.id,
        deviceToken = bearerToken,
        gallerySyncUrl = gallerySyncUrl
    )
