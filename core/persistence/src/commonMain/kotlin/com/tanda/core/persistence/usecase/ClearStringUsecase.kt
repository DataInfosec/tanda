package com.tanda.core.persistence.usecase

import com.tanda.core.common.usecase.BlockingWithArgsUseCase
import com.tanda.core.persistence.repository.PersistenceRepository
import org.koin.core.annotation.Factory

@Factory
class ClearStringUsecase(
    private val repository: PersistenceRepository
) : BlockingWithArgsUseCase<String, Unit> {
    override fun invoke(args: String) {
        return repository.remove(args)
    }
}
