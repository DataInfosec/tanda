package com.tanda.ui.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tanda.biometrics.domain.model.ScannerSessionState
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.core.ui.design.DesignButton
import com.tanda.core.ui.design.DesignText
import org.koin.core.scope.ScopeID
import org.koin.mp.KoinPlatform.getKoin

@Composable
fun SplashScreen(
    scope: ScopeID,
    scannerState: ScannerSessionState = ScannerSessionState.Starting,
    onRetry: () -> Unit = {},
) {
    val factory = getKoin().getScope(scope).get<UiComponentProvider.Factory>()
    val component = remember { factory.builder(Splash.Builder::class).build() }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (scannerState) {
                ScannerSessionState.Stopped,
                ScannerSessionState.Starting -> DesignText("Preparing scanner...")
                is ScannerSessionState.Initializing -> {
                    DesignText("Initializing scanner ${scannerState.progress}%")
                }
                is ScannerSessionState.Ready -> DesignText("Scanner ready")
                is ScannerSessionState.Error -> {
                    DesignText(scannerState.error.message ?: "Unable to initialize scanner")
                    DesignButton(
                        onClick = onRetry,
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        DesignText("Try again")
                    }
                }
            }
        }
    }
}
