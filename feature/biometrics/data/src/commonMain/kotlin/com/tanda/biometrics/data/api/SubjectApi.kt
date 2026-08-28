package com.tanda.biometrics.data.api

import com.tanda.biometrics.domain.model.Subject

interface SubjectApi {
    suspend fun get(reference: String): Subject
}
