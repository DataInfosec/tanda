package com.tanda.account.domain.usecase.device

import com.tanda.account.domain.repository.DeviceActivationRepository
import com.tanda.core.common.usecase.ObservableUseCase
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Factory

@Factory
class ObserveGallerySyncUrlUsecase(
    private val repository: DeviceActivationRepository
) : ObservableUseCase<String?> {
    override fun invoke(): Flow<String?> {
        return repository.observeGallerySyncUrl()
    }
}
