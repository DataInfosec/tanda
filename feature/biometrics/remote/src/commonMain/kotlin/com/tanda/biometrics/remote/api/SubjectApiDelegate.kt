package com.tanda.biometrics.remote.api

import com.tanda.biometrics.data.api.SubjectApi
import com.tanda.biometrics.domain.exception.SubjectException
import com.tanda.biometrics.domain.model.Subject
import com.tanda.biometrics.remote.mapper.mapToDomain
import com.tanda.biometrics.remote.model.SubjectModel
import com.tanda.core.remote.client.NetworkClient
import com.tanda.core.remote.exception.NetworkException
import io.ktor.client.HttpClient
import io.ktor.http.encodeURLParameter
import org.koin.core.annotation.Factory

@Factory
class SubjectApiDelegate(client: HttpClient) : NetworkClient(client), SubjectApi {
    override suspend fun get(reference: String): Subject {
        val response = get<List<SubjectModel>>(
            url = "$SUBJECT_PATH?external_reference=eq.${reference.encodeURLParameter()}"
        )
        if (response.isFailure) {
            val error = response.exceptionOrNull()
            throw NetworkException(error?.message, error)
        }
        return response.getOrThrow()
            .firstOrNull()
            ?.mapToDomain()
            ?: throw SubjectException()
    }

    private companion object {
        const val SUBJECT_PATH = "/v1/read/subjects"
    }
}
