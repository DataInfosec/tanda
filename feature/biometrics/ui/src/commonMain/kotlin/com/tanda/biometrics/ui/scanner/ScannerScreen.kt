package com.tanda.biometrics.ui.scanner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanda.core.ui.extension.factory
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.scope.ScopeID

@Composable
fun ScannerScreen(scope: ScopeID) {
    val factory = factory(scope)
    val component = remember { factory.builder(Scanner.Builder::class).build() }
    val viewModel: ScannerViewModel = koinViewModel(scope = component)
    val status = viewModel.status.collectAsStateWithLifecycle()
    val state = viewModel.state.collectAsStateWithLifecycle()
}
