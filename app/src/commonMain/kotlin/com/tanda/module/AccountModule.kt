package com.tanda.module

import com.tanda.account.data.api.device.DeviceActivationApi
import com.tanda.account.data.DataModule
import com.tanda.account.data.repository.DeviceActivationRepositoryDelegate
import com.tanda.account.domain.DomainModule
import com.tanda.account.domain.repository.DeviceActivationRepository
import com.tanda.account.domain.usecase.device.ActivateDeviceUsecase
import com.tanda.account.domain.usecase.device.DeviceIdUsecase
import com.tanda.account.domain.usecase.device.DeviceTokenUsecase
import com.tanda.account.domain.usecase.device.GallerySyncUrlUsecase
import com.tanda.account.domain.usecase.device.ObserveDeviceIdUsecase
import com.tanda.account.domain.usecase.device.ObserveDeviceTokenUsecase
import com.tanda.account.domain.usecase.device.ObserveGallerySyncUrlUsecase
import com.tanda.account.remote.RemoteModule
import com.tanda.account.remote.api.DeviceActivationApiDelegate
import org.koin.core.annotation.Module
import org.koin.dsl.module

@Module(
    includes = [
        DomainModule::class,
        DataModule::class,
        RemoteModule::class
    ]
)
class AccountModule {
    companion object {
        val deviceActivationModule = module {
            factory<DeviceActivationApi> { DeviceActivationApiDelegate(get()) }
            single<DeviceActivationRepository> { DeviceActivationRepositoryDelegate(get(), get()) }
            factory { ActivateDeviceUsecase(get()) }
            factory { DeviceIdUsecase(get()) }
            factory { DeviceTokenUsecase(get()) }
            factory { GallerySyncUrlUsecase(get()) }
            factory { ObserveDeviceIdUsecase(get()) }
            factory { ObserveDeviceTokenUsecase(get()) }
            factory { ObserveGallerySyncUrlUsecase(get()) }
        }
    }
}
