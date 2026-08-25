package com.tanda.biometrics.data.api

import com.tanda.biometrics.data.mapper.mapToDomain
import com.tanda.biometrics.data.model.SubjectModel
import com.tanda.biometrics.domain.model.Subject
import com.tanda.core.remote.client.NetworkClient
import com.tanda.core.remote.exception.NetworkException
import io.ktor.client.HttpClient
import io.ktor.http.encodeURLParameter
import org.koin.core.annotation.Factory

@Factory
class SubjectApiDelegate(
    client: HttpClient
) : NetworkClient(client), SubjectApi {
    override suspend fun get(externalReference: String): Subject {
        val response = get<List<SubjectModel>>(
            url = "$SUBJECT_PATH?external_reference=eq.${externalReference.encodeURLParameter()}"
        )
        if (response.isFailure) {
            val error = response.exceptionOrNull()
            throw NetworkException(error?.message, error)
        }
        return response.getOrThrow()
            .firstOrNull()
            ?.mapToDomain()
            ?: throw NetworkException("Subject not found")
    }

    private companion object {
        const val SUBJECT_PATH = "/v1/read/subjects"
    }
}
