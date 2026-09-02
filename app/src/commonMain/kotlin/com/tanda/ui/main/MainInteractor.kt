package com.tanda.ui.main

import androidx.navigation.NavController
import com.tanda.account.domain.usecase.device.DeviceIdUsecase
import com.tanda.account.domain.usecase.device.DeviceTokenUsecase
import com.tanda.account.ui.login.LoginEvent
import com.tanda.core.ui.extension.route
import com.tanda.ui.home.HomeEvent
import com.tanda.ui.setup.SetupEvent
import com.tanda.ui.splash.SplashEvent
import org.koin.core.scope.Scope

class MainInteractor(
    private val scope: Scope,
    private val controller: NavController,
    private val onStart: () -> Unit,
    private val onFinish: () -> Unit = {},
    private val deviceIdUsecase: DeviceIdUsecase = scope.get(),
    private val deviceTokenUsecase: DeviceTokenUsecase = scope.get(),
) : SplashEvent, SetupEvent, HomeEvent, LoginEvent {
    override suspend fun initialized(): Boolean {
        val token = deviceTokenUsecase()
        val deviceId = deviceIdUsecase()
        return token != null && deviceId != null
    }

    override fun invoke(event: SplashEvent.Event) {
        when (event) {
            is SplashEvent.Event.Login -> controller.route(MainNavigation.Login)
            is SplashEvent.Event.Setup -> controller.route(MainNavigation.Setup)
            is SplashEvent.Event.Home -> {
                onStart()
                controller.route(MainNavigation.Home)
            }
        }
    }

    override fun invoke(event: SetupEvent.Event) {
        when (event) {
            SetupEvent.Event.Complete -> {
                controller.route(MainNavigation.Login)
            }
            SetupEvent.Event.Dismiss -> {
                onFinish()
            }
        }
    }

    override fun invoke(event: HomeEvent.Event) {
        when (event) {
            is HomeEvent.Event.Logout -> controller.route(MainNavigation.Login)
        }
    }

    override fun invoke(event: LoginEvent.Event) {
        when (event) {
            is LoginEvent.Event.Home -> {
                onStart()
                controller.route(MainNavigation.Home)
            }
        }
    }
}
