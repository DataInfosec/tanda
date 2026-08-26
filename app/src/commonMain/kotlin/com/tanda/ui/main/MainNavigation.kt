package com.tanda.ui.main

import kotlinx.serialization.Serializable

@Serializable
sealed interface MainNavigation {
    @Serializable
    data object Splash : MainNavigation
    @Serializable
    data object Setup : MainNavigation
    @Serializable
    data object Login : MainNavigation
    @Serializable
    data object Home : MainNavigation
}
