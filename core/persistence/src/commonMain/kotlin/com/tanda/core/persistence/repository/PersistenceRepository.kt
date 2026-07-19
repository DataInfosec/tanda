package com.tanda.core.persistence.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlin.reflect.KType

interface PersistenceRepository {
    fun<T : @Serializable Any> observe(key: String, type: KType): Flow<T?>

    fun<T : @Serializable Any> get(key: String, type: KType): T?

    fun<T : @Serializable Any> set(key: String, value: T)

    fun remove(key: String)
}
