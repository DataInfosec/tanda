package com.tanda.biometrics.domain.usecase

import com.tanda.biometrics.domain.model.Image
import com.tanda.biometrics.domain.model.IdentificationResult
import com.tanda.biometrics.domain.repository.FingerprintRepository
import com.tanda.core.common.usecase.SuspendWithArgsUseCase
import org.koin.core.annotation.Factory

@Factory
class IdentificationUsecase(
    private val repository: FingerprintRepository
) : SuspendWithArgsUseCase<Image, IdentificationResult> {
    override suspend fun invoke(args: Image): IdentificationResult {
        return repository.identify(args)
    }
}
