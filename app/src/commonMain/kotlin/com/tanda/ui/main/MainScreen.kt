package com.tanda.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.tanda.biometrics.domain.usecase.StartUsecase
import org.koin.core.scope.ScopeID
import org.koin.mp.KoinPlatform.getKoin

@Composable
fun MainScreen(scope: ScopeID) {
    val current = getKoin().getScope(scope)
    val component = remember { Main.Builder(current).build() }
    val scanner = component.get<StartUsecase>()
    print(scanner.toString())
    Scaffold { paddingValues ->
        Text(text = "Main Screen", modifier = Modifier.padding(paddingValues))
    }
}
