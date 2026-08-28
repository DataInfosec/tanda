package com.tanda.biometrics.ui.subject

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tanda.core.ui.component.UiComponentProvider
import com.tanda.core.ui.design.DesignNavigation
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.scope.ScopeID

@Composable
fun SubjectScreen(scope: ScopeID) {
    val factory = getKoin().getScope(scope).get<UiComponentProvider.Factory>()
    val component = remember { factory.builder(Subject.Builder::class).build() }
    val viewModel: SubjectViewModel = koinViewModel(scope = component)
    val state = viewModel.state.collectAsStateWithLifecycle()
    val controller = rememberNavController()
    DesignNavigation(
        navController = controller,
        startDestination = "enroll"
    ) {
        composable("enroll") { SubjectPage() }
        composable("profile") { SubjectPage() }
    }
}
