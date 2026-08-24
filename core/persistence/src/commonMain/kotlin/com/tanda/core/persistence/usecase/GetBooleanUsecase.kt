package com.tanda.core.persistence.usecase

import com.tanda.core.common.usecase.BlockingWithArgsUseCase
import com.tanda.core.persistence.repository.PersistenceRepository
import org.koin.core.annotation.Factory
import kotlin.reflect.typeOf

@Factory
class GetBooleanUsecase(
    private val repository: PersistenceRepository
) : BlockingWithArgsUseCase<String, Boolean> {
    override fun invoke(args: String): Boolean {
        return repository.get(args, typeOf<Boolean>()) ?: false
    }
}
