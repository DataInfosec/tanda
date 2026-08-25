package com.tanda.biometrics.data.mapper

import com.tanda.biometrics.data.model.SubjectModel
import com.tanda.biometrics.domain.model.Subject
import kotlinx.serialization.json.jsonPrimitive

fun SubjectModel.mapToDomain(): Subject =
    Subject(
        createdAt = createdAt,
        credentialStatus = credentialStatus,
        displayName = displayName,
        externalReference = externalReference,
        id = id,
        lifecycleStatus = lifecycleStatus,
        organizationId = organizationId,
        profileCompleteness = profileCompleteness,
        profileFields = profileFields.mapValues { (_, value) ->
            value.jsonPrimitive.content
        },
        recordVerification = recordVerification,
        siteId = siteId,
        subjectType = subjectType,
        updatedAt = updatedAt,
        version = version
    )
