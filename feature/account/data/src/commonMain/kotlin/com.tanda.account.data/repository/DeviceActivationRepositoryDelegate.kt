package com.tanda.account.data.repository

import com.tanda.account.data.api.device.DeviceActivationApi
import com.tanda.account.data.mapper.device.mapToDomain
import com.tanda.account.domain.model.DeviceActivation
import com.tanda.account.domain.repository.DeviceActivationRepository
import com.tanda.core.persistence.preference.SharedPreference
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.typeOf

class DeviceActivationRepositoryDelegate(
    private val api: DeviceActivationApi,
    private val preference: SharedPreference
) : DeviceActivationRepository {
    override suspend fun activate(activationCode: String): DeviceActivation {
        return api.activate(activationCode).also { activation ->
            preference.set(DEVICE_ID_KEY, activation.deviceId)
            preference.set(DEVICE_TOKEN_KEY, activation.deviceToken)
            preference.set(GALLERY_SYNC_URL_KEY, activation.gallerySyncUrl)
        }.mapToDomain()
    }

    override fun observeDeviceId(): Flow<String?> {
        return preference.observe(DEVICE_ID_KEY, typeOf<String>())
    }

    override fun getDeviceId(): String? {
        return preference.get(DEVICE_ID_KEY, typeOf<String>())
    }

    override fun observeDeviceToken(): Flow<String?> {
        return preference.observe(DEVICE_TOKEN_KEY, typeOf<String>())
    }

    override fun getDeviceToken(): String? {
        return preference.get(DEVICE_TOKEN_KEY, typeOf<String>())
    }

    override fun observeGallerySyncUrl(): Flow<String?> {
        return preference.observe(GALLERY_SYNC_URL_KEY, typeOf<String>())
    }

    override fun getGallerySyncUrl(): String? {
        return preference.get(GALLERY_SYNC_URL_KEY, typeOf<String>())
    }

    private companion object {
        const val DEVICE_ID_KEY = "device_id"
        const val DEVICE_TOKEN_KEY = "device_token"
        const val GALLERY_SYNC_URL_KEY = "gallery_sync_url"
    }
}
