package com.tanda.biometrics.ui.subject

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanda.core.ui.design.DesignButton
import com.tanda.core.ui.design.DesignMotion
import com.tanda.core.ui.design.DesignText
import com.tanda.core.ui.design.DesignTextField
import com.tanda.core.ui.extension.designScheme
import com.tanda.core.ui.theme.DesignTheme
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import tanda.feature.biometrics.ui.generated.resources.Res
import tanda.feature.biometrics.ui.generated.resources.`continue`

@Composable
fun SubjectForm(
    title: String = "Staff ID",
    description: String = "Enter staff ID to capture biometrics data",
    hint: String = "Staff ID",
    subjectID: TextFieldState = TextFieldState(),
    isLoading: State<Boolean>,
    error: State<String?>,
    focusRequester: FocusRequester?,
    onContinue: () -> Unit
){
    val handleContinue by rememberUpdatedState(onContinue)
    var inputError by remember { mutableStateOf<String?>(null) }
    val currentError = remember {
        derivedStateOf { inputError ?: error.value }
    }
    fun validateInput(): Boolean {
        val value = subjectID.text.toString().trim()
        inputError = when {
            value.isEmpty() -> "$title is required"
            value.length < 3 -> "$title must be at least 3 characters"
            else -> null
        }
        return inputError == null
    }
    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {

        DesignText(
            text = title,
            style = MaterialTheme.typography.titleLarge,
        )

        Spacer(Modifier.height(16.dp))

        DesignText(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.designScheme.text,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(26.dp))

        DesignTextField(
            hint = hint,
            state = subjectID,
            enabled = !isLoading.value,
            hasError = currentError.value != null,
            focusRequester = focusRequester,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.fillMaxWidth(),
        )

        DesignMotion(
            targetState = currentError.value,
            modifier = Modifier.align(Alignment.Start)
        ) { error ->
            error?.let {
                DesignText(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                        .padding(horizontal = 10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        DesignButton(
            onClick = {
                if (validateInput()) {
                    handleContinue()
                }
            },
            enabled = !isLoading.value,
            isLoading = isLoading.value,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            modifier = Modifier.fillMaxWidth()
        ) {
            DesignText(stringResource(Res.string.`continue`))
        }

    }
}

@Preview
@Composable
private fun PreviewSubjectForm(){
    DesignTheme(darkTheme = false){
        SubjectForm(
            title = "Staff ID",
            description = "Enter staff ID to capture biometrics data",
            hint = "Staff ID",
            subjectID = remember { TextFieldState() },
            isLoading = remember { mutableStateOf(false) },
            error = remember { mutableStateOf(null) },
            onContinue = {},
            focusRequester = null
        )
    }
}
