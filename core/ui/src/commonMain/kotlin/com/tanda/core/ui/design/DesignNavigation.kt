package com.tanda.core.ui.design

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import kotlin.reflect.KClass

@Composable
fun DesignNavigation(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    route: String? = null,
    builder: NavGraphBuilder.() -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        contentAlignment = contentAlignment,
        route = route,
        enterTransition = {
            fadeIn(animationSpec = tween(200)) + slideInHorizontally(
                animationSpec = tween(250),
                initialOffsetX = { it / 8 }
            )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(150)) + slideOutHorizontally(
                animationSpec = tween(250),
                targetOffsetX = { -it / 8 }
            )
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(200)) + slideInHorizontally(
                animationSpec = tween(250),
                initialOffsetX = { -it / 8 }
            )
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(150)) + slideOutHorizontally(
                animationSpec = tween(250),
                targetOffsetX = { it / 8 }
            )
        },
        builder = builder
    )
}

@Composable
fun DesignNavigation(
    navController: NavHostController,
    startDestination: Any,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    route: KClass<*>? = null,
    builder: NavGraphBuilder.() -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        contentAlignment = contentAlignment,
        route = route,
        enterTransition = {
            fadeIn(animationSpec = tween(200)) + slideInHorizontally(
                animationSpec = tween(250),
                initialOffsetX = { it / 8 }
            )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(150)) + slideOutHorizontally(
                animationSpec = tween(250),
                targetOffsetX = { -it / 8 }
            )
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(200)) + slideInHorizontally(
                animationSpec = tween(250),
                initialOffsetX = { -it / 8 }
            )
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(150)) + slideOutHorizontally(
                animationSpec = tween(250),
                targetOffsetX = { it / 8 }
            )
        },
        builder = builder
    )
}
