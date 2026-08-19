package com.tanda.campus.domain.usecase

import com.tanda.campus.domain.repository.ProfileRepository
import com.tanda.core.common.usecase.ObservableUseCase
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Factory

@Factory
class ObserveProfileNameUsecase(
    private val repository: ProfileRepository
) : ObservableUseCase<String?> {
    override fun invoke(): Flow<String?> {
        return repository.observeName()
    }
}
