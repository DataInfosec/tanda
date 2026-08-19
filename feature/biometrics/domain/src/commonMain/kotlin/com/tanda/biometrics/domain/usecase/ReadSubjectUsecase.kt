package com.tanda.biometrics.domain.usecase

import com.tanda.biometrics.domain.model.Subject
import com.tanda.biometrics.domain.repository.SubjectRepository
import com.tanda.core.common.usecase.SuspendWithArgsUseCase
import org.koin.core.annotation.Factory

@Factory
class ReadSubjectUsecase(
    private val repository: SubjectRepository
) : SuspendWithArgsUseCase<ReadSubjectUsecase.Argument, Subject> {
    override suspend fun invoke(args: Argument): Subject {
        return repository.get(args.externalReference)
    }

    data class Argument(
        val externalReference: String
    )
}
