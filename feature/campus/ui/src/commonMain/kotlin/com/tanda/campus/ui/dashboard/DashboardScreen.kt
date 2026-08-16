package com.tanda.campus.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tanda.biometrics.ui.capture.BiometricCaptureScreen
import com.tanda.biometrics.ui.staff.StaffBiometricScreen
import com.tanda.biometrics.ui.student.StudentBiometricScreen
import com.tanda.core.common.interactor.LocaleInteractor
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.core.ui.design.DesignLocale
import com.tanda.core.ui.theme.DesignTheme
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.scope.ScopeID
import org.koin.mp.KoinPlatform.getKoin

@Composable
fun DashboardScreen(scope: ScopeID){
    val factory = getKoin().getScope(scope).get<UiComponentProvider.Factory>()
    val component = remember { factory.builder(Dashboard.Builder::class).build() }
    val interactor = remember { component.get<LocaleInteractor>() }
    val locale = interactor.observe().collectAsStateWithLifecycle(interactor.current())
    val viewModel: DashboardViewModel = koinViewModel(scope = component)
    val state = viewModel.state.collectAsStateWithLifecycle()
    val userName = remember {
        derivedStateOf {
            (state.value as? DashboardViewModel.State.Success)
                ?.name
                ?.takeIf { it.isNotBlank() }
                ?: "User"
        }
    }
    val controller = rememberNavController()
    LaunchedEffect(Unit) { viewModel() }

    CompositionLocalProvider(DesignLocale provides locale) {

        DesignTheme {
            NavHost(
                navController = controller,
                startDestination = DashboardRoute.Home
            ) {
                composable(DashboardRoute.Home) {
                    DashboardPage(
                        userName = userName.value,
                        onBiometricCapture = {
                            controller.navigate(DashboardRoute.BiometricCapture)
                        },
                        onStaffAttendance = {},
                        onStudentAttendance = {},
                        onExeatActivity = {},
                        onAttendanceHistory = {},
                        onDeviceInformation = {},
                        onLogout = {}
                    )
                }
                composable(DashboardRoute.BiometricCapture) {
                    BiometricCaptureScreen(
                        scope = component.id,
                        onStaffBiometricCapture = {
                            controller.navigate(DashboardRoute.StaffBiometric)
                        },
                        onStudentBiometricCapture = {
                            controller.navigate(DashboardRoute.StudentBiometric)
                        },
                        onBackClicked = {
                            controller.popBackStack()
                        }
                    )
                }
                composable(DashboardRoute.StaffBiometric) {
                    StaffBiometricScreen(
                        onBackClick = {
                            controller.popBackStack()
                        }
                    )
                }
                composable(DashboardRoute.StudentBiometric) {
                    StudentBiometricScreen(
                        onBackClick = {
                            controller.popBackStack()
                        }
                    )
                }
            }
        }
    }
}
