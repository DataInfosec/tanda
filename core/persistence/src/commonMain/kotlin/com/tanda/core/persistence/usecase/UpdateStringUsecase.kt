package com.tanda.core.persistence.usecase

import com.tanda.core.common.usecase.BlockingWithArgsUseCase
import com.tanda.core.persistence.repository.PersistenceRepository
import org.koin.core.annotation.Factory

@Factory
class UpdateStringUsecase(
    private val repository: PersistenceRepository
) : BlockingWithArgsUseCase<UpdateStringUsecase.Argument, Unit> {
    override fun invoke(args: Argument) {
        return repository.set(args.key, args.value)
    }

    data class Argument(
        val key: String,
        val value: String
    )
}
