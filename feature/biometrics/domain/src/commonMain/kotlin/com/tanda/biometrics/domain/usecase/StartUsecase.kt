package com.tanda.biometrics.domain.usecase

import com.tanda.biometrics.domain.repository.ScannerRepository
import com.tanda.core.common.usecase.BlockingUseCase
import org.koin.core.annotation.Factory

@Factory
class StartUsecase(
    private val repository: ScannerRepository
) : BlockingUseCase<Unit> {
    override fun invoke() {
        return repository.start()
    }
}
