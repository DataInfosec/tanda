package com.tanda.module

import com.tanda.BuildConstants
import com.tanda.account.domain.usecase.TokenUsecase
import com.tanda.account.remote.interceptor.createJwtInterceptor
import com.tanda.attendance.network.createDeviceAuthorizationInterceptor
import com.tanda.biometrics.domain.repository.DeviceConfigurationRepository
import com.tanda.core.remote.client.getHttpClient
import com.tanda.core.remote.interceptor.createErrorInterceptor
import io.ktor.client.HttpClient
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

@Module
class NetworkModule {
    @Single
    @Named("baseUrl")
    fun baseUrl(): String = BuildConstants.BASE_URL

    @Single
    fun provideHttpClient(
        @Named("baseUrl") baseUrl: String,
        usecase: TokenUsecase
    ): HttpClient {
        return getHttpClient(
            baseUrl = baseUrl,
            interceptors = listOf(
                createErrorInterceptor(
                    onUnauthorized = { usecase.expire() }
                ),
                createJwtInterceptor(usecase),
            )
        )
    }

    @Single
    @Named("deviceHttpClient")
    fun provideDeviceHttpClient(
        @Named("baseUrl") baseUrl: String,
        deviceConfigurationRepository: DeviceConfigurationRepository,
    ): HttpClient {
        return getHttpClient(
            baseUrl = baseUrl,
            interceptors = listOf(
                createDeviceAuthorizationInterceptor(deviceConfigurationRepository),
            )
        )
    }
}
