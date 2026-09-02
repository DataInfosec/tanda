package com.tanda.ui.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tanda.core.ui.design.DesignButton
import com.tanda.core.ui.design.DesignOutlineButton
import com.tanda.core.ui.design.DesignText
import com.tanda.core.ui.design.DesignTextField
import com.tanda.core.ui.extension.designScheme
import com.tanda.core.ui.theme.DesignTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import tanda.app.generated.resources.Res
import tanda.app.generated.resources.activation_code
import tanda.app.generated.resources.app_logo
import tanda.app.generated.resources.configure_device
import tanda.app.generated.resources.configure_device_description
import tanda.app.generated.resources.continue_button
import tanda.app.generated.resources.dismiss
import tanda.app.generated.resources.field_exact_length
import tanda.app.generated.resources.field_required
import tanda.app.generated.resources.ic_lagos
import tanda.app.generated.resources.ic_tanda


@Composable
fun SetupPage(
    isLoading: State<Boolean>,
    error: State<String?>,
    onDismissClick: () -> Unit,
    onContinueClick: (activationCode: String) -> Unit,
) {
    val handleDismiss by rememberUpdatedState(onDismissClick)
    val handleContinue by rememberUpdatedState(onContinueClick)
    var activationCodeError by remember { mutableStateOf<String?>(null) }
    val activationCode: TextFieldState = remember { TextFieldState() }

    val activationCodeLabel = stringResource(Res.string.activation_code)
    val requiredError = stringResource(Res.string.field_required, activationCodeLabel)
    val lengthError = stringResource(Res.string.field_exact_length, activationCodeLabel, ACTIVATION_CODE_LENGTH)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(58.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(Res.drawable.ic_tanda),
                    contentDescription = stringResource(Res.string.app_logo),
                    modifier = Modifier.height(36.dp)
                )
                Spacer(modifier = Modifier.padding(horizontal = 12.dp)
                    .width(1.dp)
                    .height(24.dp)
                    .background(MaterialTheme.designScheme.border))
                Image(
                    painter = painterResource(Res.drawable.ic_lagos),
                    contentDescription = stringResource(Res.string.app_logo),
                )
            }

            Spacer(Modifier.height(48.dp))

            DesignText(
                text = stringResource(Res.string.configure_device),
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(Modifier.height(12.dp))

            DesignText(
                text = stringResource(Res.string.configure_device_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.designScheme.text,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(40.dp))

            ConfigurationField(activationCodeLabel, activationCode, activationCodeError)

            error.value?.let {
                DesignText(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 10.dp, end = 10.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DesignOutlineButton(
                text = stringResource(Res.string.dismiss),
                modifier = Modifier.weight(1f).height(52.dp),
                enabled = !isLoading.value,
                onClick = { handleDismiss() },
            )

            DesignButton(
                modifier = Modifier.weight(1f).height(52.dp),
                enabled = !isLoading.value,
                isLoading = isLoading.value,
                shape = RoundedCornerShape(5.dp),
                onClick = {
                    val code = activationCode.text.toString().trim()

                    activationCodeError = validateConfigurationField(
                        value = code,
                        requiredError = requiredError,
                        lengthError = lengthError,
                    )

                    if (activationCodeError == null) {
                        handleContinue(code)
                    }
                },
            ) {
                DesignText(stringResource(Res.string.continue_button))
            }
        }
    }
}

@Composable
private fun ConfigurationField(
    label: String,
    state: TextFieldState,
    error: String?,
) {
    Column(Modifier.fillMaxWidth()) {
        DesignText(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        DesignTextField(
            state = state,
            hint = label,
            hasError = error != null,
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            DesignText(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp, start = 10.dp),
            )
        }
    }
}


private fun validateConfigurationField(
    value: String,
    requiredError: String,
    lengthError: String,
): String? = when {
    value.isBlank() -> requiredError
    value.replace("-", "").length != ACTIVATION_CODE_LENGTH -> lengthError
    else -> null
}

private const val ACTIVATION_CODE_LENGTH = 12

@Composable
@Preview
private fun PreviewSetUpPage(){
    DesignTheme(darkTheme = false) {
        SetupPage(
            isLoading = remember { mutableStateOf(false) },
            error = remember { mutableStateOf(null) },
            onDismissClick = {},
            onContinueClick = { _ -> }
        )
    }
}
