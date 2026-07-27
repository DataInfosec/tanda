package com.tanda.biometrics.verification.repository

import com.datainfosec.biometric.MobileBiometricSdk
import com.datainfosec.biometric.MobileIdentifyOutcome
import com.tanda.biometrics.domain.exception.EnrollmentException
import com.tanda.biometrics.domain.exception.FingerprintException
import com.tanda.biometrics.domain.model.Capture
import com.tanda.biometrics.domain.model.Image
import com.tanda.biometrics.domain.repository.FingerprintRepository
import com.tanda.biometrics.verification.mapper.mapToByte
import com.tanda.biometrics.verification.mapper.mapToDomain
import com.tanda.biometrics.verification.model.Credential
import org.koin.core.annotation.Singleton

@Singleton
class FingerprintRepositoryDelegate(credential: Credential) : FingerprintRepository {
    private val sdk: MobileBiometricSdk by lazy {
        MobileBiometricSdk.open(
            deviceId = credential.id,
            syncUrl = credential.url,
            storageRoot = credential.path,
            authToken = credential.secret,
            enrollmentMinQuality = credential.quality.toUByte()
        )
    }

    override suspend fun identify(image: Image): Capture {
        return when (val result = sdk.identify(image.mapToByte())) {
            is MobileIdentifyOutcome.Match -> result.mapToDomain()
            is MobileIdentifyOutcome.Retry -> throw FingerprintException(
                score = result.bestScore ?: 0f,
                reason = result.reason.name
            )
        }
    }

    override suspend fun enroll(
        id: String,
        images: List<Image>
    ) {
        require(images.isNotEmpty()) { EnrollmentException() }
        val captures = images.map { it.mapToByte() }
        sdk.enrollStudent(id, captures, null)
    }

    override suspend fun synchronize() {
        sdk.sync()
    }
}
