package com.tanda.attendance.ui.enrollment

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tanda.core.ui.design.DesignButton
import com.tanda.core.ui.design.DesignText
import com.tanda.core.ui.theme.DesignTheme
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import tanda.feature.attendance.generated.resources.Res
import tanda.feature.attendance.generated.resources.capture

@Composable
fun EnrollmentPage(
    identifier: TextFieldState,
    isLoading: State<Boolean>,
    onCapture: () -> Unit
) {
    val handleCapture by rememberUpdatedState(onCapture)
    val isEnabled = remember { derivedStateOf {
        !isLoading.value && identifier.text.isNotEmpty()
    } }
    Box(modifier = Modifier.fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .imePadding()
        .padding(horizontal = 20.dp)
        .padding(vertical = 16.dp)) {
        EnrollmentForm(
            isLoading = isLoading,
            identifier = identifier,
        )
        DesignButton(
            onClick = { handleCapture() },
            isLoading = isLoading.value,
            enabled = isEnabled.value,
            modifier = Modifier.fillMaxWidth()
                .align(Alignment.BottomCenter),
        ) { DesignText(stringResource(Res.string.capture)) }
    }
}

@Preview
@Composable
fun PreviewEnrollmentPage() {
    val identifier = remember { TextFieldState() }
    val isLoading = remember { mutableStateOf(false) }
    DesignTheme(darkTheme = false) {
        EnrollmentPage(
            identifier,
            isLoading
        ) {}
    }
}
