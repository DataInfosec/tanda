package com.tanda.core.persistence.usecase

import com.tanda.core.common.usecase.ObservableWithArgsUseCase
import com.tanda.core.persistence.repository.PersistenceRepository
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Factory
import kotlin.reflect.typeOf

@Factory
class ObservableBooleanUsecase(
    private val repository: PersistenceRepository
) : ObservableWithArgsUseCase<String, Boolean?> {
    override fun invoke(args: String): Flow<Boolean?> {
        return repository.observe(args, typeOf<Boolean>())
    }
}
