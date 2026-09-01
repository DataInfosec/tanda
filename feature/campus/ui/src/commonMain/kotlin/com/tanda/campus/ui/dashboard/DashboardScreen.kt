package com.tanda.campus.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tanda.biometrics.ui.capture.BiometricCaptureScreen
import com.tanda.attendance.ui.student.StudentAttendanceScreen
import com.tanda.biometrics.ui.scanner.ScannerScreen
import com.tanda.biometrics.ui.staff.StaffBiometricScreen
import com.tanda.biometrics.ui.student.StudentBiometricScreen
import com.tanda.core.common.interactor.LocaleInteractor
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.core.ui.design.DesignText
import com.tanda.core.ui.design.DesignButton
import com.tanda.core.ui.design.DesignLocale
import com.tanda.core.ui.design.DesignStreamState
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
            ScannerScreen(component.id) { scannerViewModel, scannerStream ->
                when (val scanner = scannerStream.value) {
                    is DesignStreamState.Success -> {
                        val ready = scanner.data
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
                                    onStudentAttendance = {
                                        controller.navigate(DashboardRoute.StudentAttendance)
                                    },
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
                                    scope = component.id,
                                    deviceId = ready.id,
                                    deviceIndex = ready.index,
                                    onBackClick = {
                                        controller.popBackStack()
                                    }
                                )
                            }
                            composable(DashboardRoute.StudentBiometric) {
                                StudentBiometricScreen(
                                    scope = component.id,
                                    deviceId = ready.id,
                                    deviceIndex = ready.index,
                                    onBackClick = {
                                        controller.popBackStack()
                                    }
                                )
                            }
                            composable(DashboardRoute.StudentAttendance) {
                                StudentAttendanceScreen(
                                    scope = component.id,
                                    deviceId = ready.id,
                                    deviceIndex = ready.index,
                                    onBackClick = {
                                        controller.popBackStack()
                                    },
                                )
                            }
                        }
                    }
                    is DesignStreamState.Error -> DashboardScannerSplash(
                        message = scanner.error.message ?: "Preparing scanner",
                    )
                    is DesignStreamState.Loading -> DashboardScannerSplash()
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun DashboardScannerSplash(
    message: String = "Preparing scanner...",
    onRetry: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DesignText(message)
            onRetry?.let {
                DesignButton(
                    onClick = it,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    DesignText("Try again")
                }
            }
        }
    }
}
