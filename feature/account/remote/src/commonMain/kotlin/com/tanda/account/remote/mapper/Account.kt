package com.tanda.account.remote.mapper

import com.tanda.account.domain.model.Account
import com.tanda.account.remote.model.UserModel

fun UserModel.mapToDomain(): Account =
    Account(
        id = id,
        name = fullName,
        username = username,
        email = email
    )
