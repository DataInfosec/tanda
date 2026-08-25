package com.tanda.campus.data.repository

import com.tanda.campus.domain.repository.ProfileRepository
import com.tanda.core.persistence.usecase.GetStringUsecase
import com.tanda.core.persistence.usecase.ObservableStringUsecase
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Factory

@Factory
class ProfileRepositoryDelegate(
    private val getStringUsecase: GetStringUsecase,
    private val observableStringUsecase: ObservableStringUsecase
) : ProfileRepository {
    override fun observeName(): Flow<String?> {
        return observableStringUsecase(USER_KEY)
    }

    override fun getName(): String? {
        return getStringUsecase(USER_KEY)
    }

    private companion object {
        const val USER_KEY = "user"
    }
}
