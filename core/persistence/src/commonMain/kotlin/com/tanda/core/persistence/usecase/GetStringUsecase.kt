package com.tanda.core.persistence.usecase

import com.tanda.core.common.usecase.BlockingWithArgsUseCase
import com.tanda.core.persistence.repository.PersistenceRepository
import org.koin.core.annotation.Factory
import kotlin.reflect.typeOf

@Factory
class GetStringUsecase(
    private val repository: PersistenceRepository
) : BlockingWithArgsUseCase<String, String?> {
    override fun invoke(args: String): String? {
        return repository.get(args, typeOf<String>())
    }
}
