package com.tanda.account.domain.usecase.device

import com.tanda.account.domain.model.DeviceActivation
import com.tanda.account.domain.repository.DeviceActivationRepository
import com.tanda.core.common.usecase.SuspendWithArgsUseCase
import org.koin.core.annotation.Factory

@Factory
class ActivateDeviceUsecase(
    private val repository: DeviceActivationRepository
) : SuspendWithArgsUseCase<ActivateDeviceUsecase.Argument, DeviceActivation> {
    override suspend fun invoke(args: Argument): DeviceActivation {
        return repository.activate(args.activationCode)
    }

    data class Argument(
        val activationCode: String,
    )
}
