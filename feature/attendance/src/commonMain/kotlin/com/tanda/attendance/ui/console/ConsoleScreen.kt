package com.tanda.attendance.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tanda.attendance.ui.checkin.CheckinScreen
import com.tanda.attendance.ui.enrollment.EnrollmentScreen
import com.tanda.biometrics.domain.model.Status
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
    ScannerScreen(component.id) { vm, stream ->
        val status = vm.status.collectAsStateWithLifecycle()
        val derivedStatus = remember { derivedStateOf {
            when(status.value) {
                is Status.Detached -> DesignStreamState.Default
                is Status.Initialize -> DesignStreamState.Success((status.value as Status.Initialize).id)
                is Status.Attached -> DesignStreamState.Success((status.value as Status.Attached).id)
                is Status.Error -> DesignStreamState.Error((status.value as Status.Error).error)
                else -> DesignStreamState.Loading
            }
        } }
        val derivedState = remember { derivedStateOf {
            when(stream.value) {
                is DesignStreamState.Default -> DesignStreamState.Default
                is DesignStreamState.Loading -> DesignStreamState.Loading
                is DesignStreamState.Success -> derivedStatus.value
                is DesignStreamState.Error -> {
                    DesignStreamState.Error((stream.value as DesignStreamState.Error).error)
                }
            }
        } }
        DesignStream(
            derivedState,
            default = { LaunchedEffect(Unit) { vm.start() } },
            loading = { ConsoleLoader() },
            error = { ConsoleError(it.value) },
        ) { state ->
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
        DisposableEffect(Unit) {
            onDispose { vm.stop() }
        }
    }
}
