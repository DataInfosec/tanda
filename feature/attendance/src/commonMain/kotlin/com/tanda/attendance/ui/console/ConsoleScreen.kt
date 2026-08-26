package com.tanda.attendance.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tanda.attendance.ui.checkin.CheckinEvent.Companion.LocalCheckinEvent
import com.tanda.attendance.ui.checkin.CheckinScreen
import com.tanda.attendance.ui.enrollment.EnrollmentEvent.Companion.LocalEnrollmentEvent
import com.tanda.attendance.ui.enrollment.EnrollmentScreen
import com.tanda.biometrics.ui.fingerprint.FingerprintScreen
import com.tanda.biometrics.ui.scanner.ScannerScreen
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.core.ui.design.DesignStream
import com.tanda.core.ui.design.DesignStreamState
import org.koin.compose.getKoin
import org.koin.core.scope.ScopeID

@Composable
fun ConsoleScreen(scope: ScopeID) {
    val factory = getKoin().getScope(scope).get<UiComponentProvider.Factory>()
    val component = remember { factory.builder(Console.Builder::class).build() }
    val controller = rememberNavController()
    ScannerScreen(component.id) { stream ->
        val derivedState = remember { derivedStateOf {
            when(stream.value) {
                is DesignStreamState.Default -> DesignStreamState.Default
                is DesignStreamState.Loading -> DesignStreamState.Loading
                is DesignStreamState.Success -> stream.value
                is DesignStreamState.Error -> {
                    DesignStreamState.Error((stream.value as DesignStreamState.Error).error)
                }
            }
        } }
        DesignStream(
            derivedState,
            loading = { ConsoleLoader() },
            error = { ConsoleError(it.value) },
        ) { state ->
            FingerprintScreen(component.id, state.value) { vm, stream ->
                val interactor = remember { ConsoleInteractor(vm, stream) }
                CompositionLocalProvider(
                    LocalEnrollmentEvent provides interactor,
                    LocalCheckinEvent provides interactor,
                ) {
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
                            EnrollmentScreen(component.id, state.value)
                        }
                        composable("checkin") {
                            CheckinScreen(component.id, state.value)
                        }
                    }
                }
            }
        }
    }
}
