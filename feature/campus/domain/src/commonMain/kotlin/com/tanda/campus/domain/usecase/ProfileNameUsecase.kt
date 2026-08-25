package com.tanda.campus.domain.usecase

import com.tanda.campus.domain.repository.ProfileRepository
import com.tanda.core.common.usecase.BlockingUseCase
import org.koin.core.annotation.Factory

@Factory
class ProfileNameUsecase(
    private val repository: ProfileRepository
) : BlockingUseCase<String?> {
    override fun invoke(): String? {
        return repository.getName()
    }
}
