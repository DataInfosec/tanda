package com.tanda.biometrics.domain.usecase

import com.tanda.biometrics.domain.repository.ScannerRepository
import com.tanda.core.common.usecase.BlockingWithArgsUseCase
import org.koin.core.annotation.Factory

@Factory
class PermissionUsecase(
    private val repository: ScannerRepository
) : BlockingWithArgsUseCase<Int, Unit> {
    override fun invoke(args: Int) {
        return repository.requestPermission(args)
    }
}
