package com.tanda.module

import com.tanda.BuildConstants
import com.tanda.account.domain.usecase.TokenUsecase
import com.tanda.account.remote.interceptor.createJwtInterceptor
import com.tanda.core.remote.client.getHttpClient
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
                createJwtInterceptor(usecase),
            )
        )
    }
}
