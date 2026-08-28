package com.tanda.biometrics.domain.usecase

import com.tanda.biometrics.domain.model.Subject
import com.tanda.biometrics.domain.repository.SubjectRepository
import com.tanda.core.common.usecase.SuspendWithArgsUseCase
import org.koin.core.annotation.Factory

@Factory
class SubjectUsecase(
    private val repository: SubjectRepository
) : SuspendWithArgsUseCase<String, Subject> {
    override suspend fun invoke(args: String): Subject {
        return repository.get(args)
    }
}
