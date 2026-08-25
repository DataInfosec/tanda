package com.tanda.biometrics.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanda.biometrics.domain.model.Subject
import com.tanda.core.ui.design.DesignButton
import com.tanda.core.ui.design.DesignOutlineButton
import com.tanda.core.ui.design.DesignText
import com.tanda.core.ui.extension.designScheme
import com.tanda.core.ui.theme.DesignTheme
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import tanda.feature.biometrics.ui.generated.resources.Res
import tanda.feature.biometrics.ui.generated.resources.capture_biometric
import tanda.feature.biometrics.ui.generated.resources.id
import tanda.feature.biometrics.ui.generated.resources.name
import tanda.feature.biometrics.ui.generated.resources.profile_completeness
import tanda.feature.biometrics.ui.generated.resources.record_verification
import tanda.feature.biometrics.ui.generated.resources.subject_type
import tanda.feature.biometrics.ui.generated.resources.use_another_id

@Composable
fun SubjectDetailPage(
    title: String,
    subject: Subject,
    onBackClick: () -> Unit = {},
    onCaptureBiometric: () -> Unit = {},
    onUseAnotherId: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 18.dp)

    ) {
        BiometricCaptureHeader(
            onBackClicked = onBackClick,
            text = title
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(top = 24.dp)
        ) {
            SubjectDetailRow(label = stringResource(Res.string.id), value = subject.externalReference)
            subject.profileFields["first_name"]?.let {
                SubjectDetailRow(label = "First name", value = it)
            }
            subject.profileFields["last_name"]?.let {
                SubjectDetailRow(label = "Last name", value = it)
            }
            SubjectDetailRow(label = stringResource(Res.string.subject_type), value = subject.subjectType)
            SubjectDetailRow(label = stringResource(Res.string.record_verification), value = subject.recordVerification)
            SubjectDetailRow(label = stringResource(Res.string.profile_completeness), value = subject.profileCompleteness)

            Spacer(modifier = Modifier.weight(1f))

            DesignButton(
                onClick = onCaptureBiometric,
                modifier = Modifier.fillMaxWidth()
            ) {
                DesignText(stringResource(Res.string.capture_biometric))
            }

            Spacer(modifier = Modifier.height(12.dp))

            DesignOutlineButton(
                text = stringResource(Res.string.use_another_id),
                onClick = onUseAnotherId,
                modifier = Modifier.fillMaxWidth().height(60.dp)
            )
        }
    }
}

@Composable
private fun SubjectDetailRow(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        DesignText(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.designScheme.text
        )
        Spacer(modifier = Modifier.height(4.dp))
        DesignText(
            text = value.ifBlank { "-" },
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.SemiBold
            )
        )

        HorizontalDivider(
            modifier = Modifier.padding(top = 18.dp)
        )
    }
}

@Preview
@Composable
private fun PreviewSubjectDetailPage() {
    DesignTheme(darkTheme = false) {
        SubjectDetailPage(
            title = "Student detail",
            subject = Subject(
                createdAt = "",
                credentialStatus = "active",
                displayName = "Sarah Johnson",
                externalReference = "STD1234",
                id = "subject-id",
                lifecycleStatus = "active",
                organizationId = "organization-id",
                profileCompleteness = "complete",
                profileFields = mapOf(
                    "first_name" to "Sarah",
                    "last_name" to "Johnson"
                ),
                recordVerification = "verified",
                siteId = "site-id",
                subjectType = "student",
                updatedAt = "",
                version = 1
            )
        )
    }
}
