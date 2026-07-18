package com.tanda.biometrics.domain.usecase

import com.tanda.biometrics.domain.model.Mode
import com.tanda.biometrics.domain.repository.ScannerRepository
import com.tanda.core.common.usecase.ObservableUseCase
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Factory

@Factory
class ObserveModeUsecase(
    private val repository: ScannerRepository
) : ObservableUseCase<Mode> {
    override fun invoke(): Flow<Mode> {
        return repository.mode
    }
}
