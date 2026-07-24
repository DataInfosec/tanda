package com.tanda.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tanda.biometrics.ui.scanner.ScannerScreen
import org.koin.core.scope.ScopeID
import org.koin.mp.KoinPlatform.getKoin

@Composable
fun MainScreen(scope: ScopeID) {
    val current = getKoin().getScope(scope)
    val component = remember { Main.Builder(current).build() }
    ScannerScreen(component.id)
}
