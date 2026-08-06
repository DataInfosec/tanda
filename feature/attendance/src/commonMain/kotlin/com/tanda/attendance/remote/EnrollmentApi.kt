package com.tanda.attendance.remote

interface EnrollmentApi {
    suspend fun start(
        externalReference: String,
        idempotencyKey: String,
    ): EnrollmentStartResult
}
