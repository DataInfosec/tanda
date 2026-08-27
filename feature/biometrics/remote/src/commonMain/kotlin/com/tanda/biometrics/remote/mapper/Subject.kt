package com.tanda.biometrics.remote.mapper

import com.tanda.biometrics.domain.model.Subject
import com.tanda.biometrics.remote.model.SubjectModel
import kotlinx.serialization.json.jsonPrimitive

fun SubjectModel.mapToDomain(): Subject {
    return Subject(
        id = id,
        status = credentialStatus,
        name = displayName,
        reference = externalReference,
        lifecycle = lifecycleStatus,
        organization = organizationId,
        state = profileCompleteness,
        properties = profileFields.mapValues { (_, value) ->
            value.jsonPrimitive.content
        },
        record = recordVerification,
        site = siteId,
        type = subjectType,
        version = version,
        updatedAt = updatedAt,
        createdAt = createdAt,
    )
}
