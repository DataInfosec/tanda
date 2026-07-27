package com.tanda.preference.repository

import com.russhwolf.settings.ObservableSettings
import com.tanda.core.persistence.repository.PersistenceRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.koin.core.annotation.Single
import kotlin.reflect.KType

@Single
@Suppress("UNCHECKED_CAST")
class PersistenceRepositoryDelegate(
    private val json: Json,
    private val settings: ObservableSettings,
) : PersistenceRepository {
    override fun <T : @Serializable Any> observe(
        key: String,
        type: KType
    ): Flow<T?> {
        return callbackFlow {
            val listener = when (type.classifier) {
                String::class -> settings.addStringOrNullListener(key) { trySend(it as T?) }
                Int::class -> settings.addIntOrNullListener(key) { trySend(it as T?) }
                Long::class -> settings.addLongOrNullListener(key) { trySend(it as T?) }
                Boolean::class -> settings.addBooleanOrNullListener(key) { trySend(it as T?) }
                Float::class -> settings.addFloatOrNullListener(key) { trySend(it as T?) }
                Double::class -> settings.addDoubleOrNullListener(key) { trySend(it as T?) }
                else -> settings.addStringOrNullListener(key) {
                    trySend(it?.let {
                        json.decodeFromString(serializer(type), it)
                    })
                }
            }
            trySend(get(key, type))
            awaitClose { listener.deactivate() }
        }
    }

    override fun <T : @Serializable Any> get(
        key: String,
        type: KType
    ): T? {
        return when (type.classifier) {
            String::class -> settings.getStringOrNull(key)
            Int::class -> settings.getIntOrNull(key)
            Long::class -> settings.getLongOrNull(key)
            Boolean::class -> settings.getBooleanOrNull(key)
            Float::class -> settings.getFloatOrNull(key)
            Double::class -> settings.getDoubleOrNull(key)
            else -> {
                settings.getStringOrNull(key)?.let {
                    json.decodeFromString(serializer(type), it)
                }
            }
        } as T?
    }

    @OptIn(InternalSerializationApi::class)
    override fun <T : @Serializable Any> set(
        key: String,
        value: T
    ) {
        when (value) {
            is String -> settings.putString(key, value)
            is Int -> settings.putInt(key, value)
            is Long -> settings.putLong(key, value)
            is Boolean -> settings.putBoolean(key, value)
            is Float -> settings.putFloat(key, value)
            is Double -> settings.putDouble(key, value)
            else -> {
                val serializer = value::class.serializer() as KSerializer<Any>
                settings.putString(
                    key,
                    json.encodeToString(serializer, value)
                )
            }
        }
    }

    override fun remove(key: String) {
        settings.remove(key)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> serializer(type: KType) =
        json.serializersModule.serializer(type) as KSerializer<T>
}
