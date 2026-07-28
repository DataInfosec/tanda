package com.tanda.core.persistence.usecase

import com.tanda.core.common.usecase.ObservableWithArgsUseCase
import com.tanda.core.persistence.repository.PersistenceRepository
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Factory
import kotlin.reflect.typeOf

@Factory
class ObservableStringUsecase(
    private val repository: PersistenceRepository
) : ObservableWithArgsUseCase<String, String?> {
    override fun invoke(args: String): Flow<String?> {
        return repository.observe(args, typeOf<String>())
    }
}
