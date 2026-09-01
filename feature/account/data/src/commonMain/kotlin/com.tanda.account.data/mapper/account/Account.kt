package com.tanda.account.data.mapper.account

import com.tanda.account.data.model.account.Profile
import com.tanda.account.domain.model.Account

fun Profile.mapToDomain(): Account {
    return Account(
        id = id,
        name = name,
        username = username,
        email = email
    )
}

fun Account.mapFromDomain(): Profile {
    return Profile(
        id = id,
        name = name,
        username = username,
        email = email
    )
}
