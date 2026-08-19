package com.tanda.attendance.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tanda.attendance.ui.checkin.CheckinScreen
import com.tanda.attendance.ui.enrollment.EnrollmentScreen
import com.tanda.biometrics.domain.model.ScannerSessionState
import com.tanda.biometrics.domain.session.ScannerSessionManager
import com.tanda.core.ui.component.UiComponentProvider
import org.koin.compose.getKoin
import org.koin.core.scope.ScopeID

@Composable
fun ConsoleScreen(scope: ScopeID) {
    val factory = getKoin().getScope(scope).get<UiComponentProvider.Factory>()
    val component = remember { factory.builder(Console.Builder::class).build() }
    val controller = rememberNavController()
    val scannerSessionManager = remember { component.get<ScannerSessionManager>() }
    val scannerState = scannerSessionManager.state.collectAsStateWithLifecycle()
    when (val scanner = scannerState.value) {
        is ScannerSessionState.Ready -> {
            NavHost(
                navController = controller,
                startDestination = "home"
            ) {
                composable("home") {
                    ConsolePage(onCheckin = { controller.navigate("checkin") }) {
                        controller.navigate("enrol")
                    }
                }
                composable("enrol") {
                    EnrollmentScreen(component.id, scanner.deviceId)
                }
                composable("checkin") {
                    CheckinScreen(component.id, scanner.deviceId)
                }
            }
        }
        is ScannerSessionState.Error -> ConsoleError(scanner.error)
        else -> ConsoleLoader()
    }
}
