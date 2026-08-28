package com.tanda.biometrics.data.repository

import com.tanda.biometrics.data.api.SubjectApi
import com.tanda.biometrics.domain.model.Subject
import com.tanda.biometrics.domain.repository.SubjectRepository
import org.koin.core.annotation.Singleton

@Singleton
class SubjectRepositoryDelegate(
    private val api: SubjectApi
) : SubjectRepository {
    override suspend fun get(reference: String): Subject {
        return api.get(reference)
    }
}
