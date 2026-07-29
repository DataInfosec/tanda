package com.tanda.attendance.ui.console

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.tanda.core.ui.design.DesignText
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
            loading = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { DesignText("Loading..", style = MaterialTheme.typography.titleMedium.copy(
                    color = MaterialTheme.colorScheme.error
                )) }
            },
            error = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { DesignText("Error: ${(it.value.cause ?: it.value)::class.simpleName}") }
            },
        ) { state ->
            NavHost(
                navController = controller,
                startDestination = "home"
            ) {
                composable("home") {
                    ConsolePage(onCheckin = {
                        controller.navigate("checkin")
                    }) {
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
        LaunchedEffect(Unit) { vm.start() }
        DisposableEffect(Unit) {
            onDispose { vm.stop() }
        }
    }
}
