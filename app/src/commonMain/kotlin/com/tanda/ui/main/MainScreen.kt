package com.tanda.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tanda.account.ui.login.LoginScreen
import com.tanda.core.common.interactor.LocaleInteractor
import com.tanda.core.ui.design.DesignLocale
import com.tanda.core.ui.theme.DesignTheme
import com.tanda.ui.home.HomeScreen
import com.tanda.ui.splash.SplashScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.scope.ScopeID
import org.koin.mp.KoinPlatform.getKoin

@Composable
fun MainScreen(scope: ScopeID) {
    val current = getKoin().getScope(scope)
    val component = remember { Main.Builder(current).build() }
    val interactor = remember { component.get<LocaleInteractor>() }
    val locale = interactor.observe().collectAsStateWithLifecycle(interactor.current())
    val viewModel: MainViewModel = koinViewModel(scope = component)
    val controller = rememberNavController()
    LaunchedEffect(Unit) { viewModel() }
    LaunchedEffect(Unit) {
        viewModel.state
            .filterIsInstance<MainViewModel.State.Success>()
            .distinctUntilChanged()
            .collectLatest { state ->
                controller.navigate(if (state.authenticated) "home" else "login") {
                    popUpTo("splash") {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }
    }
    CompositionLocalProvider(DesignLocale provides locale) {
        DesignTheme {
            NavHost(
                navController = controller,
                startDestination = "splash"
            ) {
                composable("splash") { SplashScreen(component.id) }
                composable("home") { HomeScreen(component.id) }
                composable("login") { LoginScreen(component.id) }
            }
        }
    }
}
