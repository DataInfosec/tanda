package com.tanda.account.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanda.core.ui.design.DesignButton
import com.tanda.core.ui.design.DesignMotion
import com.tanda.core.ui.design.DesignText
import com.tanda.core.ui.extension.designScheme
import com.tanda.core.ui.theme.DesignTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import tanda.feature.account.ui.generated.resources.Res
import tanda.feature.account.ui.generated.resources.ic_lagos
import tanda.feature.account.ui.generated.resources.ic_tanda

@Composable
fun LoginPage(
    email: TextFieldState,
    password: TextFieldState,
    isLoading: State<Boolean>,
    error: State<String?>,
    onContinue: () -> Unit
) {
    val handleContinue by rememberUpdatedState(onContinue)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(.15f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(Res.drawable.ic_tanda),
                contentDescription = "app logo",
                modifier = Modifier.height(36.dp)
            )
            Spacer(modifier = Modifier.padding(horizontal = 12.dp)
                .width(1.dp)
                .height(24.dp)
                .background(MaterialTheme.designScheme.border))
            Image(
                painter = painterResource(Res.drawable.ic_lagos),
                contentDescription = "app logo",
            )
        }
        Spacer(modifier = Modifier.height(42.dp))
        DesignText(
            text = "Sign in to continue",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        DesignText(
            text = "Enter email address and password to log in to your\naccount for attendance activities.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.designScheme.text,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))
        LoginForm(
            email = email,
            password = password,
            isLoading = isLoading
        )
        DesignMotion(
            targetState = error.value,
            modifier = Modifier.align(Alignment.Start)
        ) { error ->
            error?.let {
                DesignText(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp)
                        .padding(horizontal = 10.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
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
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Preview
@Composable
fun PreviewLoginPage() {
    val error = remember { mutableStateOf("Error occurred...") }
    DesignTheme(darkTheme = false) {
        LoginPage(
            email = remember { TextFieldState() },
            password = remember { TextFieldState() },
            isLoading = remember { mutableStateOf(false) },
            error = error,
            onContinue = {}
        )
    }
}
