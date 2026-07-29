package com.tanda.attendance.ui.enrollment

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.tanda.core.ui.design.DesignTextField
import org.jetbrains.compose.resources.stringResource
import tanda.feature.attendance.generated.resources.Res
import tanda.feature.attendance.generated.resources.identity

@Composable
fun EnrollmentForm(
    isLoading: State<Boolean>,
    identifier: TextFieldState
) {
    val focusRequester = remember { FocusRequester() }
    val animatable = remember { Animatable(0f) }
    val keyboardController = LocalSoftwareKeyboardController.current
    DesignTextField(
        hint = stringResource(Res.string.identity),
        state = identifier,
        enabled = !isLoading.value,
        focusRequester = focusRequester,
        modifier = Modifier.fillMaxWidth(),
    )
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        animatable.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1)
        )
        keyboardController?.show()
    }
}
