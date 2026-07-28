package com.tanda.attendance.ui.enrollment

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tanda.biometrics.ui.scanner.ScannerScreen
import com.tanda.core.ui.component.UiComponentProvider
import org.koin.compose.getKoin
import org.koin.core.scope.ScopeID

@Composable
fun EnrollmentScreen(scope: ScopeID) {
    val factory = getKoin().getScope(scope).get<UiComponentProvider.Factory>()
    val component = remember { factory.builder(Enrollment.Builder::class).build() }
    val controller = rememberNavController()
    NavHost(
        navController = controller,
        startDestination = "home"
    ) {
        composable("home") {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = { controller.navigate("scan") }) {
                    Text("click me")
                }
            }
        }
        composable("scan") {
            ScannerScreen(component.id)
        }
    }
}
