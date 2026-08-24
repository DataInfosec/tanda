package com.tanda.attendance.ui.student

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanda.attendance.domain.model.AttendanceOption
import com.tanda.core.ui.design.DesignButton
import com.tanda.core.ui.design.DesignLoader
import com.tanda.core.ui.design.DesignOutlineButton
import com.tanda.core.ui.design.DesignText
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tanda.feature.attendance.generated.resources.Res
import tanda.feature.attendance.generated.resources.attendance_points_empty
import tanda.feature.attendance.generated.resources.attendance_points_retry
import tanda.feature.attendance.generated.resources.ic_arrow_left
import tanda.feature.attendance.generated.resources.ic_right_arrow
import tanda.feature.attendance.generated.resources.manage_student_attendance
import tanda.feature.attendance.generated.resources.student_attendance

@Composable
fun StudentAttendancePage(
    state: StudentAttendanceViewModel.State,
    onBackClick: () -> Unit,
    onOptionClick: (AttendanceOption) -> Unit,
    onRetryClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        AttendanceHeader(onBackClick = onBackClick)
        Spacer(Modifier.height(48.dp))
        DesignText(
            text = stringResource(Res.string.manage_student_attendance),
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(32.dp))

        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                DesignLoader()
            }

            state.options.isEmpty() -> EmptyAttendancePoints(
                message = state.errorMessage
                    ?: stringResource(Res.string.attendance_points_empty),
                onRetryClick = onRetryClick,
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.usingCachedPoints) {
                    DesignText(
                        text = "Unable to refresh points. Showing saved attendance points.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                state.options.forEach { option ->
                    DesignOutlineButton(
                        text = option.label,
                        modifier = Modifier.height(64.dp),
                        trailing = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_right_arrow),
                                contentDescription = null,
                            )
                        },
                        onClick = { onOptionClick(option) },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AttendanceHeader(onBackClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_left),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        DesignText(
            text = stringResource(Res.string.student_attendance),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun EmptyAttendancePoints(
    message: String,
    onRetryClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DesignText(text = message)
        Spacer(Modifier.height(16.dp))
        DesignButton(onClick = onRetryClick) {
            DesignText(stringResource(Res.string.attendance_points_retry))
        }
    }
}
