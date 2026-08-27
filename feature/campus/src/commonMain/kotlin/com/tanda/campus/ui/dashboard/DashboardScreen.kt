package com.tanda.campus.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tanda.biometrics.ui.capture.CaptureEvent.Companion.LocalCaptureEvent
import com.tanda.biometrics.ui.capture.CaptureOption
import com.tanda.biometrics.ui.capture.CaptureScreen
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.core.ui.design.DesignNavigation
import com.tanda.core.ui.design.DesignStream
import com.tanda.core.ui.design.DesignStreamState
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.scope.ScopeID
import org.koin.mp.KoinPlatform.getKoin

@Composable
fun DashboardScreen(scope: ScopeID) {
    val factory = getKoin().getScope(scope).get<UiComponentProvider.Factory>()
    val component = remember { factory.builder(Dashboard.Builder::class).build() }
    val viewModel: DashboardViewModel = koinViewModel(scope = component)
    val state = viewModel.state.collectAsStateWithLifecycle()
    val derivedState = remember { derivedStateOf {
        when(state.value) {
            is DashboardViewModel.State.Default -> DesignStreamState.Default
            is DashboardViewModel.State.Loading -> DesignStreamState.Loading
            is DashboardViewModel.State.Success -> {
                DesignStreamState.Success((state.value
                        as DashboardViewModel.State.Success).account)
            }
            is DashboardViewModel.State.Error -> {
                DesignStreamState.Error((state.value
                        as DashboardViewModel.State.Error).error)
            }
        }
    } }
    val controller = rememberNavController()
    val interactor = remember { DashboardInteractor(controller) }
    CompositionLocalProvider(LocalCaptureEvent provides interactor) {
        DesignNavigation(
            navController = controller,
            startDestination = "dashboard"
        ) {
            composable("dashboard") {
                DesignStream(derivedState) {
                    DashboardPage(
                        userName = it.value.username,
                        onBiometricCapture = { controller.navigate("biometrics") }
                    )
                }
            }
            composable("biometrics") {
                CaptureOption(onStudentBiometricCapture = { controller.navigate("subject") })
            }
            composable("subject") { CaptureScreen(component.id) }
        }
    }
}
