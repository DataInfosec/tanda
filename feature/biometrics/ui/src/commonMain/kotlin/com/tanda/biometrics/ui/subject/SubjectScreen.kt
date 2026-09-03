package com.tanda.biometrics.ui.subject

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanda.core.ui.component.UiComponentProvider
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.scope.ScopeID

@Composable
fun SubjectScreen(
    scope: ScopeID,
    config: SubjectBiometricUiConfig = SubjectBiometricUiConfig(subjectName = "Staff"),
    onBackClick: () -> Unit = {}
) {
    val localEvent = SubjectEvent.LocalSubjectEvent.current
    val factory = getKoin().getScope(scope).get<UiComponentProvider.Factory>()
    val component = remember { factory.builder(Subject.Builder::class).build() }
    val viewModel: SubjectViewModel = koinViewModel(scope = component)
    val state = viewModel.state.collectAsStateWithLifecycle()
    val subjectID = remember { TextFieldState() }
    val searchQuery = remember { TextFieldState() }
    val isLoading: State<Boolean> = remember {
        derivedStateOf { state.value is SubjectViewModel.State.Loading }
    }
    val error: State<String?> = remember {
        derivedStateOf { (state.value as? SubjectViewModel.State.Error)?.error?.message }
    }
    val subject = (state.value as? SubjectViewModel.State.Success)?.subject

    if (subject != null) {
        SubjectProfile(
            title = config.detailTitle,
            subject = subject,
            onBackClick = onBackClick,
            onCaptureBiometric = { localEvent(SubjectEvent.Event.Capture) },
            onUseAnotherId = { viewModel.reset() }
        )
    } else {
        SubjectPage(
            config = config,
            subjectID = subjectID,
            searchQuery = searchQuery,
            isLoading = isLoading,
            error = error,
            onBackClick = onBackClick,
            onContinue = {
                viewModel.readSubject(
                    reference = subjectID.text.toString().trim(),
                    expectedSubjectType = config.expectedSubjectType
                )
            }
        )
    }
}
