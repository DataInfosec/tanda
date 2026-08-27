package com.tanda.ui.main

import androidx.navigation.NavController
import com.tanda.account.ui.login.LoginEvent
import com.tanda.core.persistence.usecase.GetStringUsecase
import com.tanda.core.persistence.usecase.SetStringUsecase
import com.tanda.core.ui.extension.route
import com.tanda.ui.home.HomeEvent
import com.tanda.ui.setup.SetupEvent
import com.tanda.ui.splash.SplashEvent
import org.koin.core.scope.Scope

class MainInteractor(
    private val scope: Scope,
    private val controller: NavController,
    private val onStart: () -> Unit,
    private val getStringUsecase: GetStringUsecase = scope.get(),
    private val setStringUsecase: SetStringUsecase = scope.get(),
) : SplashEvent, SetupEvent, HomeEvent, LoginEvent {
    override fun initialized(): Boolean {
        val token = getStringUsecase(args = DEVICE_TOKEN)
        val deviceId = getStringUsecase(args = DEVICE_ID)
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
            is SetupEvent.Event.Complete -> {
                setStringUsecase(SetStringUsecase.Argument(key = DEVICE_TOKEN, event.token))
                setStringUsecase(SetStringUsecase.Argument(key = DEVICE_ID, event.id))
                controller.route(MainNavigation.Login)
            }
            is SetupEvent.Event.Dismiss -> {
                //TODO finish the activity
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
    companion object{
        const val DEVICE_TOKEN = "device token"
        const val DEVICE_ID = "device id"

    }
}
