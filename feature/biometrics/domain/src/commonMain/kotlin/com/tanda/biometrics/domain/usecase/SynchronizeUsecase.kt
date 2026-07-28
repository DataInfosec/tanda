package com.tanda.biometrics.domain.usecase

import com.tanda.biometrics.domain.repository.FingerprintRepository
import com.tanda.core.common.usecase.SuspendUseCase
import org.koin.core.annotation.Factory

@Factory
class SynchronizeUsecase(
    private val repository: FingerprintRepository
) : SuspendUseCase<Unit> {
    override suspend fun invoke() {
        return repository.synchronize()
    }
}
