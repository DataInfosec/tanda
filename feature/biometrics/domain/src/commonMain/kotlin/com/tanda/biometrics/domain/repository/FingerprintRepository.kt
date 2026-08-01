package com.tanda.biometrics.domain.repository

import com.tanda.biometrics.domain.model.Image
import com.tanda.biometrics.domain.model.Capture

interface FingerprintRepository {
    suspend fun identify(image: Image): Capture

    suspend fun enroll(id: String, images: List<Image>, batchId: String? = null): String

    suspend fun synchronize()
}
