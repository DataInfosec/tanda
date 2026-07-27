package com.tanda.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanda.biometrics.ui.scanner.ScannerScreen
import com.tanda.core.common.interactor.LocaleInteractor
import org.koin.core.scope.ScopeID
import org.koin.mp.KoinPlatform.getKoin

@Composable
fun MainScreen(scope: ScopeID) {
    val current = getKoin().getScope(scope)
    val component = remember { Main.Builder(current).build() }
    val localeInteractor = remember { component.get<LocaleInteractor>() }
    val locale by localeInteractor.observe()
        .collectAsStateWithLifecycle(localeInteractor.current())
    key(locale.code) {
        ScannerScreen(component.id)
    }
}
