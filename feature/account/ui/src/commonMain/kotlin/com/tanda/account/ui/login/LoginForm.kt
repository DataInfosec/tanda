package com.tanda.account.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanda.core.ui.design.DesignTextField
import org.jetbrains.compose.resources.painterResource
import tanda.feature.account.ui.generated.resources.Res
import tanda.feature.account.ui.generated.resources.visibility
import tanda.feature.account.ui.generated.resources.visibility_off

@Composable
fun LoginForm(
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
