package com.tanda.biometrics.domain.usecase

import com.tanda.biometrics.domain.model.Status
import com.tanda.biometrics.domain.repository.ScannerRepository
import com.tanda.core.common.usecase.ObservableUseCase
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Factory

@Factory
class ObserveStatusUsecase(
    private val repository: ScannerRepository
) : ObservableUseCase<Status> {
    override fun invoke(): Flow<Status> {
        return repository.status
    }
}
