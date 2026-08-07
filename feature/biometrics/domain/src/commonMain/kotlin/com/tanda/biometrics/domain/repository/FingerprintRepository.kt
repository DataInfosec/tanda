package com.tanda.biometrics.domain.repository

import com.tanda.biometrics.domain.model.Image
import com.tanda.biometrics.domain.model.IdentificationResult

interface FingerprintRepository {
    suspend fun identify(image: Image): IdentificationResult

    suspend fun enroll(id: String, images: List<Image>, batchId: String? = null): String

    suspend fun synchronize()
}
