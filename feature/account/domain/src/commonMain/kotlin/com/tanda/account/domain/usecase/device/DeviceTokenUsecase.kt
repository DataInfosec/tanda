package com.tanda.account.domain.usecase.device

import com.tanda.account.domain.repository.DeviceActivationRepository
import com.tanda.core.common.usecase.SuspendUseCase
import org.koin.core.annotation.Factory

@Factory
class DeviceTokenUsecase(
    private val repository: DeviceActivationRepository
) : SuspendUseCase<String?> {
    override suspend fun invoke(): String? {
        return repository.getDeviceToken()
    }
}
