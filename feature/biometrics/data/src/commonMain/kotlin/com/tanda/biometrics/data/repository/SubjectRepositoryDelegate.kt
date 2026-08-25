package com.tanda.biometrics.data.repository

import com.tanda.biometrics.data.api.SubjectApi
import com.tanda.biometrics.domain.model.Subject
import com.tanda.biometrics.domain.repository.SubjectRepository
import org.koin.core.annotation.Factory

@Factory
class SubjectRepositoryDelegate(
    private val api: SubjectApi
) : SubjectRepository {
    override suspend fun get(externalReference: String): Subject {
        return api.get(externalReference)
    }
}
