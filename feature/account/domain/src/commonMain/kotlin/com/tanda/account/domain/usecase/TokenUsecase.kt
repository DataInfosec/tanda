package com.tanda.account.domain.usecase

import com.tanda.account.domain.repository.TokenRepository
import com.tanda.core.common.usecase.SuspendUseCase
import org.koin.core.annotation.Factory

@Factory
class TokenUsecase(
    private val repository: TokenRepository
) : SuspendUseCase<String?> {
    override suspend fun invoke(): String? {
        return repository.get()
    }
}
