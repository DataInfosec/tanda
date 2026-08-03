package com.tanda.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tanda.attendance.ui.console.ConsoleScreen
import com.tanda.core.common.interactor.LocaleInteractor
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.core.ui.design.DesignLocale
import com.tanda.core.ui.theme.DesignTheme
import org.koin.core.scope.ScopeID
import org.koin.mp.KoinPlatform.getKoin

@Composable
fun HomeScreen(scope: ScopeID) {
    val factory = getKoin().getScope(scope).get<UiComponentProvider.Factory>()
    val component = remember { factory.builder(Home.Builder::class).build() }
    val interactor = remember { component.get<LocaleInteractor>() }
    val locale = interactor.observe().collectAsStateWithLifecycle(interactor.current())
    val controller = rememberNavController()
    CompositionLocalProvider(DesignLocale provides locale) {
        DesignTheme {
            NavHost(
                navController = controller,
                startDestination = "home"
            ) {
                composable("home") {
                    HomePage { controller.navigate("attendance") }
                }
                composable("attendance") { ConsoleScreen(component.id) }
            }
        }
    }
}
