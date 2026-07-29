package com.tanda.attendance.ui.checkin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanda.biometrics.ui.fingerprint.FingerprintScreen
import com.tanda.core.ui.component.UiComponentProvider
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.scope.ScopeID

@Composable
fun CheckinScreen(
    scope: ScopeID,
    deviceId: Int
) {
    val factory = getKoin().getScope(scope).get<UiComponentProvider.Factory>()
    val component = remember { factory.builder(Checkin.Builder::class).build() }
    val viewModel: CheckinViewModel = koinViewModel(scope = component)
    val state = viewModel.state.collectAsStateWithLifecycle()
    FingerprintScreen(component.id, deviceId) { vm, stream ->

    }
}
