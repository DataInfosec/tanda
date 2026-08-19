package com.tanda.biometrics.domain.repository

import com.tanda.biometrics.domain.model.Subject

interface SubjectRepository {
    suspend fun get(externalReference: String): Subject
}
