package com.tanda.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tanda.account.ui.login.LoginEvent.Companion.LocalLoginEvent
import com.tanda.account.ui.login.LoginScreen
import com.tanda.core.ui.design.DesignLocale
import com.tanda.core.ui.theme.DesignTheme
import com.tanda.ui.home.HomeEvent.Companion.LocalHomeEvent
import com.tanda.ui.home.HomeScreen
import com.tanda.ui.setup.SetupEvent.Companion.LocalSetupEvent
import com.tanda.ui.setup.SetupScreen
import com.tanda.ui.splash.SplashEvent.Companion.LocalSplashEvent
import com.tanda.ui.splash.SplashScreen
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.scope.ScopeID
import org.koin.mp.KoinPlatform.getKoin

@Composable
fun MainScreen(scope: ScopeID) {
    val current = getKoin().getScope(scope)
    val component = remember { Main.Builder(current).build() }
    val viewModel: MainViewModel = koinViewModel(scope = component)
    val locale = viewModel.locale.collectAsStateWithLifecycle()
    val controller = rememberNavController()
    val interactor = remember { MainInteractor(
        scope = component,
        controller = controller,
        onStart = viewModel::invoke
    ) }
    CompositionLocalProvider(
        DesignLocale provides locale,
        LocalSplashEvent provides interactor,
        LocalSetupEvent provides interactor,
        LocalHomeEvent provides interactor,
        LocalLoginEvent provides interactor,
    ) {
        DesignTheme {
            NavHost(
                navController = controller,
                startDestination = MainNavigation.Splash
            ) {
                composable<MainNavigation.Splash> { SplashScreen(component.id) }
                composable<MainNavigation.Setup> { SetupScreen(component.id) }
                composable<MainNavigation.Home> { HomeScreen(component.id) }
                composable<MainNavigation.Login> { LoginScreen(component.id) }
            }
        }
    }
}
