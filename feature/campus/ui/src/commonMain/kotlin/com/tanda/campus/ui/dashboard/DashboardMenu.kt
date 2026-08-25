package com.tanda.campus.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import tanda.feature.campus.ui.generated.resources.Res
import tanda.feature.campus.ui.generated.resources.attendance_history
import tanda.feature.campus.ui.generated.resources.device_information
import tanda.feature.campus.ui.generated.resources.ic_attendance
import tanda.feature.campus.ui.generated.resources.ic_logout
import tanda.feature.campus.ui.generated.resources.logout

@Composable
fun DashboardMenu(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    onAttendanceHistory: () -> Unit = {},
    onDeviceInformation: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier
            .width(240.dp)
            .background(
                Color.White,
                RoundedCornerShape(18.dp)
            )
    ) {

        MenuItem(
            icon = Res.drawable.ic_attendance,
            text = Res.string.attendance_history
        ) {
            onDismiss()
            onAttendanceHistory()
        }
        HorizontalDivider()

        MenuItem(
            icon = Res.drawable.ic_attendance,
            text = Res.string.device_information
        ) {
            onDismiss()
            onDeviceInformation()
        }

        HorizontalDivider()

        MenuItem(
            icon = Res.drawable.ic_logout,
            text = Res.string.logout,
            textColor = Color.Red,
            iconTint = Color.Red
        ) {
            onDismiss()
            onLogout()
        }
    }
}

@Composable
fun MenuItem(
    icon: DrawableResource,
    text: StringResource,
    textColor: Color = Color(0xFF263238),
    iconTint: Color = Color(0xFF263238),
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = iconTint
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = stringResource(text),
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        },
        onClick = onClick
    )
}

@Preview
@Composable
fun PreviewDashboardMenu(){
    DashboardMenu(expanded = true)
}
