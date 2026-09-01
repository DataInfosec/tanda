package com.tanda.attendance.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tanda.attendance.ui.checkin.CheckinScreen
import com.tanda.attendance.ui.enrollment.EnrollmentScreen
import com.tanda.biometrics.ui.scanner.ScannerScreen
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.core.ui.design.DesignStreamState
import org.koin.compose.getKoin
import org.koin.core.scope.ScopeID

@Composable
fun ConsoleScreen(scope: ScopeID) {
    val factory = getKoin().getScope(scope).get<UiComponentProvider.Factory>()
    val component = remember { factory.builder(Console.Builder::class).build() }
    val controller = rememberNavController()
    ScannerScreen(component.id) { _, scannerStream ->
        when (val scanner = scannerStream.value) {
            is DesignStreamState.Success -> {
                val ready = scanner.data
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
                        EnrollmentScreen(component.id, ready.id)
                    }
                    composable("checkin") {
                        CheckinScreen(component.id, ready.id)
                    }
                }
            }
            is DesignStreamState.Error -> ConsoleError(scanner.error)
            else -> ConsoleLoader()
        }
    }
}
