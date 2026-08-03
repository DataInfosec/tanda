package com.tanda.account.remote.mapper

import com.tanda.account.data.model.Authentication
import com.tanda.account.remote.model.AuthenticationModel

fun AuthenticationModel.mapToDomain(): Authentication =
    Authentication(
        token = token,
        account = user.mapToDomain()
    )
