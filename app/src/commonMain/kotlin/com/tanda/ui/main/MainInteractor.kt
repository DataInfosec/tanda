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
        return true
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
                //Todo Set device Id And token
                controller.route(MainNavigation.Login)
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
