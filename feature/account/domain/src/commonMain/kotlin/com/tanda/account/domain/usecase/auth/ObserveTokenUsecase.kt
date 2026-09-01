package com.tanda.account.domain.usecase.auth

import com.tanda.account.domain.repository.TokenRepository
import com.tanda.core.common.usecase.ObservableUseCase
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Factory

@Factory
class ObserveTokenUsecase(
    private val repository: TokenRepository
) : ObservableUseCase<String?> {
    override fun invoke(): Flow<String?> {
        return repository.observe()
    }
}