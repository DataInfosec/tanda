package com.tanda.biometrics.domain.usecase

import com.tanda.biometrics.domain.model.Snapshot
import com.tanda.biometrics.domain.repository.ScannerRepository
import com.tanda.core.common.usecase.ObservableUseCase
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Factory

@Factory
class ObserveStateUsecase(
    private val repository: ScannerRepository
) : ObservableUseCase<Snapshot> {
    override fun invoke(): Flow<Snapshot> {
        return repository.state
    }
}
