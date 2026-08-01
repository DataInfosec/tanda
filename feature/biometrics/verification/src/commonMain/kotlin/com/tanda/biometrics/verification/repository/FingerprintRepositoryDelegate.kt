package com.tanda.biometrics.verification.repository

import com.datainfosec.biometric.MobileBiometricSdk
import com.datainfosec.biometric.MobileDeviceTokenProvider
import com.datainfosec.biometric.MobileIdentifyOutcome
import com.datainfosec.biometric.MobileSubjectEnrollmentAuthorization
import com.tanda.biometrics.domain.exception.EnrollmentException
import com.tanda.biometrics.domain.exception.FingerprintException
import com.tanda.biometrics.domain.model.Capture
import com.tanda.biometrics.domain.model.Image
import com.tanda.biometrics.domain.repository.FingerprintRepository
import com.tanda.biometrics.verification.mapper.mapToByte
import com.tanda.biometrics.verification.mapper.mapToDomain
import com.tanda.biometrics.verification.model.Credential
import org.koin.core.annotation.Singleton
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Singleton
class FingerprintRepositoryDelegate(
    credential: Credential,
    tokenProvider: MobileDeviceTokenProvider
) : FingerprintRepository {
    private val sdk: MobileBiometricSdk by lazy {
        MobileBiometricSdk.open(
            deviceInstanceId = credential.id,
            syncUrl = credential.url,
            storageRoot = credential.path,
            tokenProvider = tokenProvider,
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

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun enroll(
        id: String,
        images: List<Image>,
        batchId: String?
    ): String {
        require(images.isNotEmpty()) { EnrollmentException() }
        val captures = images.map { it.mapToByte() }
        val batchId = batchId ?: Uuid.random().toString()
        val authorization = MobileSubjectEnrollmentAuthorization(
            subjectId = id,
            enrollmentOperationId = batchId
        )
        val result = sdk.enrollSubject(authorization, captures)
        if (result.submissionId == null) {
            throw EnrollmentException()
        }
        return batchId
    }

    override suspend fun synchronize() {
        sdk.sync()
    }
}
