package com.tanda.biometrics.domain.usecase

import com.tanda.biometrics.domain.model.Image
import com.tanda.biometrics.domain.repository.FingerprintRepository
import com.tanda.core.common.usecase.SuspendWithArgsUseCase
import org.koin.core.annotation.Factory

@Factory
class EnrollmentUsecase(
    private val repository: FingerprintRepository
) : SuspendWithArgsUseCase<EnrollmentUsecase.Argument, String> {
    override suspend fun invoke(args: Argument): String {
        return repository.enroll(
            id = args.id,
            images = args.images,
            session = args.session
        )
    }

    data class Argument(
        val id: String,
        val images: List<Image>,
        val session: String? = null
    )
}
