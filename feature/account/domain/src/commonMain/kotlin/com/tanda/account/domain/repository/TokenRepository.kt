package com.tanda.account.domain.repository

import kotlinx.coroutines.flow.Flow

interface TokenRepository {
    fun observe(): Flow<String?>

    fun observeExpiration(): Flow<Unit>

    fun get(): String?

    fun clear()

    fun expire()
}
