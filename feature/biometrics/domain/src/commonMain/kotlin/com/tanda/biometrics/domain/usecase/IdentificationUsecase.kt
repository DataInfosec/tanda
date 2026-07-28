package com.tanda.biometrics.domain.usecase

import com.tanda.biometrics.domain.model.Capture
import com.tanda.biometrics.domain.model.Image
import com.tanda.biometrics.domain.repository.FingerprintRepository
import com.tanda.core.common.usecase.SuspendWithArgsUseCase
import org.koin.core.annotation.Factory

@Factory
class IdentificationUsecase(
    private val repository: FingerprintRepository
) : SuspendWithArgsUseCase<Image, Capture> {
    override suspend fun invoke(args: Image): Capture {
        return repository.identify(args)
    }
}
