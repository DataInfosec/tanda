package com.tanda.ui.main

import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tanda.account.ui.login.LoginScreen
import com.tanda.campus.ui.dashboard.DashboardScreen
import com.tanda.core.common.interactor.LocaleInteractor
import com.tanda.core.ui.design.DesignLocale
import com.tanda.core.ui.theme.DesignTheme
import com.tanda.ui.splash.SplashScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.scope.ScopeID
import org.koin.mp.KoinPlatform.getKoin

@Composable
fun MainScreen(
    scope: ScopeID,
    onExitApplication: () -> Unit = {},
) {
    val current = getKoin().getScope(scope)
    val component = remember { Main.Builder(current).build() }
    val interactor = remember { component.get<LocaleInteractor>() }
    val locale = interactor.observe().collectAsStateWithLifecycle(interactor.current())
    val viewModel: MainViewModel = koinViewModel(scope = component)
    val controller = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val state = viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel() }
    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                MainViewModel.Effect.SessionExpired -> {
                    snackbar.showSnackbar("Session expired")
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.state
            .filterIsInstance<MainViewModel.State.Success>()
            .distinctUntilChanged()
            .collectLatest { auth ->
                when {
                    !auth.deviceConfigured -> MainRoute.Splash
                    !auth.authenticated -> MainRoute.Login
                    else -> MainRoute.Dashboard
                }.let { destination ->
                    controller.navigate(destination) {
                        popUpTo(MainRoute.Graph) {
                            inclusive = false
                            saveState = false
                        }
                        launchSingleTop = true
                        restoreState = false
                    }
                }
            }
    }
    CompositionLocalProvider(DesignLocale provides locale) {
        DesignTheme {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) }
            ) {
                NavHost(
                    navController = controller,
                    startDestination = MainRoute.Splash,
                    route = MainRoute.Graph
                ) {
                    composable(MainRoute.Splash) {
                        SplashScreen(
                            scope = component.id,
                        )
                    }
                    composable(MainRoute.Dashboard) { DashboardScreen(component.id) }
                    composable(MainRoute.Login) { LoginScreen(component.id) }
                }
            }
            if ((state.value as? MainViewModel.State.Success)?.deviceConfigured == false) {
                DeviceConfigurationDialog(
                    onDismissClick = onExitApplication,
                    onContinueClick = viewModel::configureDevice,
                )
            }
        }
    }
}
