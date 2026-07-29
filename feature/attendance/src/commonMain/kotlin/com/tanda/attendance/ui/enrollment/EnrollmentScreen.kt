package com.tanda.attendance.ui.enrollment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tanda.biometrics.ui.fingerprint.FingerprintScreen
import com.tanda.core.ui.component.UiComponentProvider
import org.koin.compose.getKoin
import org.koin.core.scope.ScopeID

@Composable
fun EnrollmentScreen(
    scope: ScopeID,
    deviceId: Int
) {
    val factory = getKoin().getScope(scope).get<UiComponentProvider.Factory>()
    val component = remember { factory.builder(Enrollment.Builder::class).build() }
    FingerprintScreen(component.id, deviceId)
}
