package com.tanda.biometrics.domain.usecase

import com.tanda.biometrics.domain.model.Event
import com.tanda.biometrics.domain.repository.ScannerRepository
import com.tanda.core.common.usecase.ObservableUseCase
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Factory

@Factory
class ObserveEventUsecase(
    private val repository: ScannerRepository
) : ObservableUseCase<Event> {
    override fun invoke(): Flow<Event> {
        return repository.event
    }
}
