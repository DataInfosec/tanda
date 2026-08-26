package com.tanda.ui.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.core.ui.design.DesignText
import com.tanda.ui.splash.SplashEvent.Companion.LocalSplashEvent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.scope.ScopeID
import org.koin.mp.KoinPlatform.getKoin

@Composable
fun SplashScreen(scope: ScopeID) {
    val localEvent = LocalSplashEvent.current
    val factory = getKoin().getScope(scope).get<UiComponentProvider.Factory>()
    val component = remember { factory.builder(Splash.Builder::class).build() }
    val viewModel: SplashViewModel = koinViewModel(scope = component)
    LaunchedEffect(Unit) { viewModel() }
    LaunchedEffect(Unit) {
        viewModel.state
            .filterIsInstance<SplashViewModel.State.Success>()
            .distinctUntilChanged()
            .collectLatest { state ->
                if (!localEvent.initialized()) {
                    localEvent(SplashEvent.Event.Setup)
                } else {
                    localEvent(if (state.authenticated) {
                        SplashEvent.Event.Home
                    } else SplashEvent.Event.Login)
                }
            }
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        DesignText("Splash Screen")
    }
}
