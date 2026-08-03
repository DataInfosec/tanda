package com.tanda.account.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanda.core.ui.design.DesignButton
import com.tanda.core.ui.design.DesignText
import com.tanda.core.ui.design.DesignTextField
import com.tanda.core.ui.theme.AppFontFamily
import com.tanda.core.ui.theme.DesignTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import tanda.feature.account.ui.generated.resources.Res
import tanda.feature.account.ui.generated.resources.app_logo
import tanda.feature.account.ui.generated.resources.visibility
import tanda.feature.account.ui.generated.resources.visibility_off

@Composable
fun LoginPage(
    email: TextFieldState,
    password: TextFieldState,
    isLoading: State<Boolean>,
    onContinue: () -> Unit
) {
    val handleContinue by rememberUpdatedState(onContinue)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Image(
                painter = painterResource(Res.drawable.app_logo),
                contentDescription = "app logo",
                modifier = Modifier.width(150.dp).height(60.dp)
            )
            Spacer(modifier = Modifier.height(65.dp))

            DesignText(
                text = "Sign in to continue",
                style = TextStyle(
                    fontFamily = AppFontFamily(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                )
            )

            Spacer(modifier = Modifier.height(30.dp))

            DesignText(
                text = "Enter email address and password to log in to your\naccount for attendance activities.",
                style = TextStyle(
                    fontFamily = AppFontFamily(),
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            )
            Spacer(modifier = Modifier.height(34.dp))

            LoginForm(
                email = email,
                password = password,
                isLoading = isLoading
            )
            Spacer(modifier = Modifier.height(55.dp))

            DesignButton(
                onClick = { handleContinue() },
                enabled = !isLoading.value,
                isLoading = isLoading.value,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                modifier = Modifier.fillMaxWidth()
            ) {
                DesignText("Continue")
            }
        }
    }
}

@Composable
private fun LoginForm(
    email: TextFieldState,
    password: TextFieldState,
    isLoading: State<Boolean>
) {
    var isObscured by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(35.dp)) {

        DesignTextField(
            hint = "Email address",
            state = email,
            enabled = !isLoading.value,
            shape = RoundedCornerShape(7.dp),
            fontWeight = FontWeight.Normal,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp, horizontal = 12.dp)
        )

        DesignTextField(
            hint = "Password",
            state = password,
            enabled = !isLoading.value,
            shape = RoundedCornerShape(7.dp),
            fontWeight = FontWeight.Normal,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.fillMaxWidth(),
            trailing = {
                val icon = if (isObscured) Res.drawable.visibility_off else Res.drawable.visibility
                val description = if (isObscured) "show password" else "hide password"
                IconButton(
                    onClick = { isObscured = !isObscured },
                    content = {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = description,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                )
            },
            contentPadding = PaddingValues(horizontal = 12.dp)
        )
    }
}


@Preview
@Composable
fun PreviewLoginPage() {
    DesignTheme(darkTheme = false) {
        LoginPage(
            email = remember { TextFieldState() },
            password = remember { TextFieldState() },
            isLoading = remember { mutableStateOf(false) },
            onContinue = {}
        )
    }
}
