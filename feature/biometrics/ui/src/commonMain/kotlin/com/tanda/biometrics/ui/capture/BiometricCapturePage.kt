package com.tanda.biometrics.ui.capture

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanda.core.ui.design.DesignOutlineButton
import com.tanda.core.ui.design.DesignText
import com.tanda.core.ui.theme.DesignTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import tanda.feature.biometrics.ui.generated.resources.Res
import tanda.feature.biometrics.ui.generated.resources.biometric
import tanda.feature.biometrics.ui.generated.resources.capture_or_view_details
import tanda.feature.biometrics.ui.generated.resources.ic_arrow_left
import tanda.feature.biometrics.ui.generated.resources.ic_right_arrow
import tanda.feature.biometrics.ui.generated.resources.ic_staff_biometric
import tanda.feature.biometrics.ui.generated.resources.ic_student_biometric
import tanda.feature.biometrics.ui.generated.resources.staff_biometric
import tanda.feature.biometrics.ui.generated.resources.student_biometric

@Composable
fun BiometricCapturePage(
    modifier: Modifier = Modifier,
    onStaffBiometricCapture: () -> Unit = {},
    onStudentBiometricCapture: () -> Unit = {},
    onBackClicked: () -> Unit = {}
) {
    val handleStudentBiometric by rememberUpdatedState(onStudentBiometricCapture)
    val handleStaffBiometric by rememberUpdatedState(onStaffBiometricCapture)
    val handleBackClicked by rememberUpdatedState(onBackClicked)
    Column(
        modifier = modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BiometricCaptureHeader(onBackClicked = { handleBackClicked() })

        Spacer(modifier.height(40.dp))

        DesignText(
            text = stringResource(Res.string.biometric),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier.height(20.dp))

        DesignText(
            text = stringResource(Res.string.capture_or_view_details),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier.height(35.dp))

        DesignOutlineButton(
            text = stringResource(Res.string.staff_biometric),
            onClick = { handleStaffBiometric() },
            leading = {
                Icon(
                    painter = painterResource(Res.drawable.ic_staff_biometric),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailing = {
                Icon(
                    painter = painterResource(Res.drawable.ic_right_arrow),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            modifier = Modifier.height(70.dp).background(
                MaterialTheme.colorScheme.background
            ),
            containerColor = MaterialTheme.colorScheme.background
        )
        Spacer(modifier.height(25.dp))

        DesignOutlineButton(
            text = stringResource(Res.string.student_biometric),
            onClick = { handleStudentBiometric() },
            leading = {
                Icon(
                    painter = painterResource(Res.drawable.ic_student_biometric),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailing = {
                Icon(
                    painter = painterResource(Res.drawable.ic_right_arrow),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            modifier = Modifier.height(70.dp).background(
                MaterialTheme.colorScheme.background
            ),
            containerColor = MaterialTheme.colorScheme.background
        )
    }
}

@Composable
fun BiometricCaptureHeader(
    onBackClicked: () -> Unit,
    text: String = stringResource(Res.string.biometric),
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IconButton(
            onClick = onBackClicked,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_left),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}


@Preview()
@Composable
fun PreviewBiometricCapture() {
    DesignTheme(darkTheme = false) {
        BiometricCapturePage()
    }

}
