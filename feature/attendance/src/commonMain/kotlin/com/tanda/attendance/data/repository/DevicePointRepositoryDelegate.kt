package com.tanda.attendance.data.repository

import com.tanda.attendance.data.mapper.mapToDomain
import com.tanda.attendance.data.model.DevicePointResponse
import com.tanda.attendance.data.remote.DevicePoint
import com.tanda.attendance.domain.model.AttendancePoint
import com.tanda.attendance.domain.repository.DevicePointRepository
import com.tanda.biometrics.domain.repository.DeviceConfigurationRepository
import com.tanda.core.persistence.repository.PersistenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Singleton
import kotlin.reflect.typeOf

@Singleton
class DevicePointRepositoryDelegate(
    private val remote: DevicePoint,
    private val persistence: PersistenceRepository,
    private val deviceConfigurationRepository: DeviceConfigurationRepository,
) : DevicePointRepository {
    override fun observe(subjectType: String): Flow<List<AttendancePoint>> {
        val configuration = deviceConfigurationRepository.get() ?: return flowOf(emptyList())
        return persistence.observe<DevicePointResponse>(
            key = cacheKey(configuration.deviceInstanceId, subjectType),
            type = typeOf<DevicePointResponse>(),
        ).map { response ->
            response
                ?.takeIf { it.subjectType.equals(subjectType, ignoreCase = true) }
                ?.points
                ?.map { it.mapToDomain() }
                .orEmpty()
        }
    }

    override suspend fun refresh(subjectType: String) {
        val configuration = requireNotNull(deviceConfigurationRepository.get()) {
            "Device configuration is required"
        }
        val response = remote.get()
        require(response.deviceInstanceId == configuration.deviceInstanceId) {
            "Device point response does not belong to this device"
        }
        require(response.subjectType.equals(subjectType, ignoreCase = true)) {
            "Expected $subjectType attendance points but received ${response.subjectType}"
        }
        persistence.set(
            key = cacheKey(configuration.deviceInstanceId, subjectType),
            value = response,
        )
    }

    override suspend fun get(pointId: String, subjectType: String): AttendancePoint? {
        val configuration = deviceConfigurationRepository.get() ?: return null
        return persistence.get<DevicePointResponse>(
            key = cacheKey(configuration.deviceInstanceId, subjectType),
            type = typeOf<DevicePointResponse>(),
        )
            ?.takeIf { it.subjectType.equals(subjectType, ignoreCase = true) }
            ?.points
            ?.firstOrNull { it.id == pointId }
            ?.mapToDomain()
    }

    private fun cacheKey(deviceInstanceId: String, subjectType: String): String {
        return "device_points_${deviceInstanceId}_${subjectType.lowercase()}"
    }
}
