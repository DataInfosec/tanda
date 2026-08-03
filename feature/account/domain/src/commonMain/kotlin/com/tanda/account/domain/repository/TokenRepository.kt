package com.tanda.account.domain.repository

import kotlinx.coroutines.flow.Flow

interface TokenRepository {
    fun observe(): Flow<String?>

    fun get(): String?

    fun clear()
}
