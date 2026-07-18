package com.tanda.biometrics.domain.usecase

import com.tanda.biometrics.domain.model.State
import com.tanda.biometrics.domain.repository.ScannerRepository
import com.tanda.core.common.usecase.ObservableUseCase
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Factory

@Factory
class ObserveStateUsecase(
    private val repository: ScannerRepository
) : ObservableUseCase<State> {
    override fun invoke(): Flow<State> {
        return repository.state
    }
}
