package com.tanda.core.ui.extension

import androidx.navigation.NavController

fun NavController.route(route: String) {
    navigate(route) {
        popUpTo(graph.id) {
            inclusive = true
        }
        launchSingleTop = true
    }
}

fun <T : Any> NavController.route(route: T) {
    navigate(route) {
        popUpTo(graph.id) {
            inclusive = true
        }
        launchSingleTop = true
    }
}
