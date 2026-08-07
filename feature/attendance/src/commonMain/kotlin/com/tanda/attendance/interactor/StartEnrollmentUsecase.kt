package com.tanda.attendance.interactor

import com.tanda.attendance.remote.EnrollmentApi
import com.tanda.attendance.remote.EnrollmentStartResult
import org.koin.core.annotation.Factory

@Factory
class StartEnrollmentUsecase(
    private val api: EnrollmentApi,
) {
    suspend operator fun invoke(
        externalReference: String,
        idempotencyKey: String,
    ): EnrollmentStartResult {
        return api.start(
            externalReference = externalReference,
            idempotencyKey = idempotencyKey,
        )
    }
}
