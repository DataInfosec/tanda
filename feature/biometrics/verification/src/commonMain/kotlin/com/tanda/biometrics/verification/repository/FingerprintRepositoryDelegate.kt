package com.tanda.biometrics.verification.repository

import com.datainfosec.biometric.MobileBiometricSdk
import com.datainfosec.biometric.MobileDeviceTokenProvider
import com.datainfosec.biometric.MobileIdentifyOutcome
import com.tanda.biometrics.domain.exception.ClockEventException
import com.tanda.biometrics.domain.exception.EnrollmentException
import com.tanda.biometrics.domain.exception.FingerprintException
import com.tanda.biometrics.domain.model.AttendanceType
import com.tanda.biometrics.domain.model.Capture
import com.tanda.biometrics.domain.model.Image
import com.tanda.biometrics.domain.repository.FingerprintRepository
import com.tanda.biometrics.verification.mapper.mapToByte
import com.tanda.biometrics.verification.mapper.mapToData
import com.tanda.biometrics.verification.mapper.mapToDomain
import com.tanda.biometrics.verification.model.Credential
import org.koin.core.annotation.Singleton
import kotlin.uuid.ExperimentalUuidApi

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
                score = result.bestScore ?: UNDEFINED_SCORE,
                threshold = result.bestVerificationScore ?: UNDEFINED_SCORE,
                reason = result.reason.name
            )
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun enroll(id: String, images: List<Image>) {
        require(images.isNotEmpty()) { EnrollmentException() }
        val captures = images.map { it.mapToByte() }
        val result = sdk.enrollSubject(id, captures)
        if (result.submissionId == null) {
            throw EnrollmentException()
        }
    }

    override suspend fun clockActivities(
        pointID: String,
        captureEvidence: Capture,
        mobileAttendanceType: AttendanceType
    ): String {
         try {
           return sdk.queueClockEvent(
                attendancePointId = pointID,
                eventType = mobileAttendanceType.mapToData(),
                evidence = captureEvidence.mapToData()
            )
        }catch (e: Throwable){
            throw ClockEventException(e.message ?: "Unknown error")
        }
    }

    override suspend fun synchronize() {
        sdk.sync()
    }


    private companion object {
        const val UNDEFINED_SCORE = -1f
    }
}
