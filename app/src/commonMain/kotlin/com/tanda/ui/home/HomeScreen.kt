package com.tanda.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tanda.attendance.ui.attendance.AttendanceEvent.Companion.LocalAttendanceEvent
import com.tanda.attendance.ui.attendance.AttendanceScreen
import com.tanda.attendance.ui.console.ConsoleScreen
import com.tanda.campus.ui.dashboard.DashboardScreen
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.core.ui.theme.DesignTheme
import com.tanda.ui.home.HomeEvent.Companion.LocalHomeEvent
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.scope.ScopeID
import org.koin.mp.KoinPlatform.getKoin

@Composable
fun HomeScreen(scope: ScopeID) {
    val localEvent = LocalHomeEvent.current
    val factory = getKoin().getScope(scope).get<UiComponentProvider.Factory>()
    val component = remember { factory.builder(Home.Builder::class).build() }
    val viewModel: HomeViewModel = koinViewModel(scope = component)
    val state = viewModel.state.collectAsStateWithLifecycle()
    val isLoggedOut = remember { derivedStateOf {
        (state.value as? HomeViewModel.State.Success?)?.authenticated == false
    } }
    val controller = rememberNavController()
    val interactor = remember { HomeInteractor(controller) }
    CompositionLocalProvider(LocalAttendanceEvent provides interactor) {
        DesignTheme {
            NavHost(
                navController = controller,
                startDestination = "dashboard"
            ) {
                composable("dashboard") { DashboardScreen(component.id) }
                composable("attendance") { AttendanceScreen(component.id) }
                composable("console") { ConsoleScreen(component.id) }
            }
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { isLoggedOut.value }
            .collectLatest {
                if (it) {
                    localEvent(HomeEvent.Event.Logout)
                }
            }
    }
}
