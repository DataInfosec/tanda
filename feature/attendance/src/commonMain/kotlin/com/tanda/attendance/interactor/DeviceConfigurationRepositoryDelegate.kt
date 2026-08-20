package com.tanda.attendance.interactor

import com.tanda.biometrics.domain.model.DeviceConfiguration
import com.tanda.biometrics.domain.repository.DeviceConfigurationRepository
import com.tanda.core.persistence.usecase.GetStringUsecase
import com.tanda.core.persistence.usecase.ObservableStringUsecase
import com.tanda.core.persistence.usecase.UpdateStringUsecase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.koin.core.annotation.Singleton

@Singleton
class DeviceConfigurationRepositoryDelegate(
    private val getStringUsecase: GetStringUsecase,
    private val observableStringUsecase: ObservableStringUsecase,
    private val updateStringUsecase: UpdateStringUsecase,
) : DeviceConfigurationRepository {
    override fun get(): DeviceConfiguration? {
        return configurationOf(
            deviceInstanceId = getStringUsecase(DEVICE_INSTANCE_ID_KEY),
            fingerprintToken = getStringUsecase(FINGERPRINT_TOKEN_KEY),
        )
    }

    override fun observe(): Flow<DeviceConfiguration?> {
        return combine(
            observableStringUsecase(DEVICE_INSTANCE_ID_KEY),
            observableStringUsecase(FINGERPRINT_TOKEN_KEY),
            ::configurationOf,
        )
    }

    override fun save(configuration: DeviceConfiguration) {
        updateStringUsecase(
            UpdateStringUsecase.Argument(
                key = DEVICE_INSTANCE_ID_KEY,
                value = configuration.deviceInstanceId.trim(),
            )
        )
        updateStringUsecase(
            UpdateStringUsecase.Argument(
                key = FINGERPRINT_TOKEN_KEY,
                value = configuration.fingerprintToken.trim(),
            )
        )
    }

    private fun configurationOf(
        deviceInstanceId: String?,
        fingerprintToken: String?,
    ): DeviceConfiguration? {
        val id = deviceInstanceId?.trim().orEmpty()
        val token = fingerprintToken?.trim().orEmpty()
        if (id.isEmpty() || token.isEmpty()) return null

        return DeviceConfiguration(
            deviceInstanceId = id,
            fingerprintToken = token,
        )
    }

    private companion object {
        const val DEVICE_INSTANCE_ID_KEY = "device_instance_id"
        const val FINGERPRINT_TOKEN_KEY = "fingerprint_token"
    }
}
