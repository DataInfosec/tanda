package com.tanda.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tanda.core.ui.design.DesignButton
import com.tanda.core.ui.design.DesignMotion
import com.tanda.core.ui.design.DesignOutlineButton
import com.tanda.core.ui.design.DesignText
import com.tanda.core.ui.design.DesignTextField
import com.tanda.core.ui.extension.designScheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tanda.app.generated.resources.Res
import tanda.app.generated.resources.configure_device
import tanda.app.generated.resources.configure_device_description
import tanda.app.generated.resources.continue_button
import tanda.app.generated.resources.device_instance_id
import tanda.app.generated.resources.dismiss
import tanda.app.generated.resources.field_minimum_length
import tanda.app.generated.resources.field_required
import tanda.app.generated.resources.fingerprint_token
import tanda.app.generated.resources.ic_tanda

@Composable
fun DeviceConfigurationDialog(
    onDismissClick: () -> Unit,
    onContinueClick: (deviceInstanceId: String, fingerprintToken: String) -> Unit,
) {
    val handleDismissClick by rememberUpdatedState(onDismissClick)
    val handleContinueClick by rememberUpdatedState(onContinueClick)
    val deviceInstanceId = remember { TextFieldState() }
    val fingerprintToken = remember { TextFieldState() }
    var deviceIdError by remember { mutableStateOf<String?>(null) }
    var fingerprintTokenError by remember { mutableStateOf<String?>(null) }
    val deviceIdLabel = stringResource(Res.string.device_instance_id)
    val tokenLabel = stringResource(Res.string.fingerprint_token)
    val deviceIdRequiredError = stringResource(Res.string.field_required, deviceIdLabel)
    val tokenRequiredError = stringResource(Res.string.field_required, tokenLabel)
    val deviceIdLengthError = stringResource(
        Res.string.field_minimum_length,
        deviceIdLabel,
        MINIMUM_FIELD_LENGTH,
    )
    val tokenLengthError = stringResource(
        Res.string.field_minimum_length,
        tokenLabel,
        MINIMUM_FIELD_LENGTH,
    )

    fun validate(value: String, requiredError: String, lengthError: String): String? {
        return when {
            value.isEmpty() -> requiredError
            value.length < MINIMUM_FIELD_LENGTH -> lengthError
            else -> null
        }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_tanda),
                    contentDescription = null,
                    modifier = Modifier.height(36.dp),
                )

                Spacer(modifier = Modifier.height(28.dp))

                DesignText(
                    text = stringResource(Res.string.configure_device),
                    style = MaterialTheme.typography.titleLarge,
                )

                Spacer(modifier = Modifier.height(12.dp))

                DesignText(
                    text = stringResource(Res.string.configure_device_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.designScheme.text,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(24.dp))

                ConfigurationField(
                    label = deviceIdLabel,
                    state = deviceInstanceId,
                    error = deviceIdError,
                )

                Spacer(modifier = Modifier.height(16.dp))

                ConfigurationField(
                    label = tokenLabel,
                    state = fingerprintToken,
                    error = fingerprintTokenError,
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DesignOutlineButton(
                        text = stringResource(Res.string.dismiss),
                        onClick = { handleDismissClick() },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                    )
                    DesignButton(
                        onClick = {
                            val id = deviceInstanceId.text.toString().trim()
                            val token = fingerprintToken.text.toString().trim()
                            deviceIdError = validate(
                                value = id,
                                requiredError = deviceIdRequiredError,
                                lengthError = deviceIdLengthError,
                            )
                            fingerprintTokenError = validate(
                                value = token,
                                requiredError = tokenRequiredError,
                                lengthError = tokenLengthError,
                            )
                            if (deviceIdError == null && fingerprintTokenError == null) {
                                handleContinueClick(id, token)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(5.dp),
                    ) {
                        DesignText(stringResource(Res.string.continue_button))
                    }
                }
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
    Column(modifier = Modifier.fillMaxWidth()) {
        DesignText(
            text = label,
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        DesignTextField(
            hint = label,
            state = state,
            hasError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.fillMaxWidth(),
        )
        DesignMotion(targetState = error) { message ->
            message?.let {
                DesignText(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp, start = 10.dp, end = 10.dp),
                )
            }
        }
    }
}

private const val MINIMUM_FIELD_LENGTH = 3
