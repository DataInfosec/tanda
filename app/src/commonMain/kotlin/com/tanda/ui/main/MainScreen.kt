package com.tanda.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tanda.attendance.ui.console.ConsoleScreen
import com.tanda.core.common.interactor.LocaleInteractor
import com.tanda.core.ui.design.DesignButton
import com.tanda.core.ui.design.DesignLocale
import com.tanda.core.ui.design.DesignText
import com.tanda.core.ui.theme.DesignTheme
import org.koin.core.scope.ScopeID
import org.koin.mp.KoinPlatform.getKoin

@Composable
fun MainScreen(scope: ScopeID) {
    val current = getKoin().getScope(scope)
    val component = remember { Main.Builder(current).build() }
    val localeInteractor = remember { component.get<LocaleInteractor>() }
    val locale = localeInteractor.observe()
        .collectAsStateWithLifecycle(localeInteractor.current())
    val controller = rememberNavController()
    CompositionLocalProvider(DesignLocale provides locale) {
        DesignTheme {
            NavHost(
                navController = controller,
                startDestination = "home"
            ) {
                composable("home") {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        DesignButton(
                            onClick = { controller.navigate("console") },
                            modifier = Modifier.fillMaxWidth(),
                        ) { DesignText("Start") }
                    }
                }
                composable("console") { ConsoleScreen(component.id) }
            }
        }
    }
}
