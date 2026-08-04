package com.tanda.account.ui.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tanda.core.ui.design.DesignPassword
import com.tanda.core.ui.design.DesignTextField
import com.tanda.core.ui.theme.DesignTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import tanda.feature.account.ui.generated.resources.Res
import tanda.feature.account.ui.generated.resources.email_hint
import tanda.feature.account.ui.generated.resources.hide_password
import tanda.feature.account.ui.generated.resources.ic_hide
import tanda.feature.account.ui.generated.resources.ic_show
import tanda.feature.account.ui.generated.resources.password_hint
import tanda.feature.account.ui.generated.resources.show_password

@Composable
fun LoginForm(
    email: TextFieldState,
    password: TextFieldState,
    isLoading: State<Boolean>,
    focusRequester: FocusRequester? = null,
) {
    var isObscured by remember { mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DesignTextField(
            hint = stringResource(Res.string.email_hint),
            state = email,
            enabled = !isLoading.value,
            focusRequester = focusRequester,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.fillMaxWidth(),
        )
        DesignPassword(
            hint = stringResource(Res.string.password_hint),
            state = password,
            enabled = !isLoading.value,
            modifier = Modifier.fillMaxWidth(),
            textObfuscationMode = if (isObscured) {
                TextObfuscationMode.RevealLastTyped
            } else {
                TextObfuscationMode.Visible
            },
            trailing = {
                val icon = if (isObscured) Res.drawable.ic_hide else Res.drawable.ic_show
                val description = if (isObscured) {
                    stringResource(Res.string.show_password)
                } else {
                    stringResource(Res.string.hide_password)
                }
                Icon(
                    painter = painterResource(icon),
                    contentDescription = description,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(end = 4.dp)
                        .clip(CircleShape)
                        .size(18.dp)
                        .clickable { isObscured = !isObscured }
                )
            },
        )
    }
}

@Preview
@Composable
fun PreviewLoginForm() {
    DesignTheme(darkTheme = false) {
        LoginForm(
            email = remember { TextFieldState() },
            password = remember { TextFieldState() },
            isLoading = remember { mutableStateOf(false) }
        )
    }
}
