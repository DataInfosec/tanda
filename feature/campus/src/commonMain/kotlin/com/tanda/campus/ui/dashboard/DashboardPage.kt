package com.tanda.campus.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.tanda.core.ui.design.DesignText
import com.tanda.core.ui.extension.designScheme
import com.tanda.core.ui.theme.DesignTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import tanda.feature.campus.generated.resources.Res
import tanda.feature.campus.generated.resources.biometric_capture
import tanda.feature.campus.generated.resources.dashboard_prompt
import tanda.feature.campus.generated.resources.exeat_activity
import tanda.feature.campus.generated.resources.ic_attendance
import tanda.feature.campus.generated.resources.ic_exeat
import tanda.feature.campus.generated.resources.ic_fingerprint
import tanda.feature.campus.generated.resources.ic_menu
import tanda.feature.campus.generated.resources.ic_person
import tanda.feature.campus.generated.resources.ic_tanda
import tanda.feature.campus.generated.resources.powered_brand
import tanda.feature.campus.generated.resources.powered_by
import tanda.feature.campus.generated.resources.profile_picture
import tanda.feature.campus.generated.resources.staff_attendance
import tanda.feature.campus.generated.resources.student_attendance
import tanda.feature.campus.generated.resources.welcome

@Composable
fun DashboardPage(
    onBiometricCapture: () -> Unit = {},
    onStudentAttendance: () -> Unit = {},
    onStaffAttendance: () -> Unit = {},
    onExeatActivity: () -> Unit = {},
    onAttendanceHistory: () -> Unit = {},
    onDeviceInformation: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val handleBiometricCapture by rememberUpdatedState(onBiometricCapture)
    val handleStudentAttendance by rememberUpdatedState(onStudentAttendance)
    val handleStaffAttendance by rememberUpdatedState(onStaffAttendance)
    val handleExeatActivity by rememberUpdatedState(onExeatActivity)
    val handleAttendanceHistory by rememberUpdatedState(onAttendanceHistory)
    val handleDeviceInformation by rememberUpdatedState(onDeviceInformation)
    val handleLogout by rememberUpdatedState(onLogout)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        DashboardHeader(
            onAttendanceHistory = handleAttendanceHistory,
            onDeviceInformation = handleDeviceInformation,
            onLogout = handleLogout
        )

        Spacer(modifier = Modifier.height(34.dp))

        ProfileSection(
            userName = "Bello Yakub",
            imageUrl = "1234rfgt"
        )

        Spacer(modifier = Modifier.height(38.dp))

        DesignText(
            text = stringResource(Res.string.dashboard_prompt),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(38.dp))

        DashboardCard(
            title = Res.string.biometric_capture,
            icon = Res.drawable.ic_fingerprint,
            onItemClick = handleBiometricCapture
        )

        Spacer(modifier = Modifier.height(16.dp))

        DashboardCard(
            title = Res.string.student_attendance,
            icon = Res.drawable.ic_attendance,
            onItemClick = handleStudentAttendance
        )

        Spacer(modifier = Modifier.height(16.dp))

        DashboardCard(
            title = Res.string.staff_attendance,
            icon = Res.drawable.ic_attendance,
            onItemClick = handleStaffAttendance
        )

        Spacer(modifier = Modifier.height(16.dp))

        DashboardCard(
            title = Res.string.exeat_activity,
            icon = Res.drawable.ic_exeat,
            onItemClick = handleExeatActivity
        )

        Spacer(modifier = Modifier.weight(1f))
        Footer()
    }
}

@Composable
fun Footer(versionNumber: String = "Version: 1.0.0") {
    Column(
        modifier = Modifier.fillMaxWidth().padding(paddingValues = PaddingValues(top = 10.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,

        ) {
        DesignText(
            text = stringResource(Res.string.powered_by),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.designScheme.text,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        DesignText(
            text = stringResource(Res.string.powered_brand),
            style = MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.designScheme.text,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center
            )
        )
        Spacer(modifier = Modifier.height(42.dp))
        DesignText(
            text = versionNumber,
            style = MaterialTheme.typography.bodyMedium.copy(
                textAlign = TextAlign.Center
            ),
        )
    }
}


@Composable
fun DashboardHeader(
    onAttendanceHistory: () -> Unit,
    onDeviceInformation: () -> Unit,
    onLogout: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_tanda),
                contentDescription = null,
                modifier = Modifier.height(36.dp)
            )
            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = { expanded = true }
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_menu),
                    contentDescription = null,
                )
            }
        }
        DashboardMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            modifier = Modifier.align(alignment = Alignment.TopEnd),
            onAttendanceHistory = onAttendanceHistory,
            onDeviceInformation = onDeviceInformation,
            onLogout = onLogout
        )
    }
}

@Composable
fun ProfileSection(
    userName: String,
    imageUrl: String = ""
) {
    Row {
        SubcomposeAsyncImage(
            model = imageUrl,
            contentDescription = stringResource(Res.string.profile_picture),
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape).border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                ),
            loading = { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) },
            error = {
                Image(
                    painter = painterResource(Res.drawable.ic_person),
                    contentDescription = null
                )
            }
        )

        Spacer(Modifier.width(14.dp))

        Column {
            DesignText(
                stringResource(Res.string.welcome),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.designScheme.text
            )

            DesignText(
                userName,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}


@Preview
@Composable
fun PreviewDashboardPage() {
    DesignTheme(darkTheme = false) {
        DashboardPage()
    }
}
