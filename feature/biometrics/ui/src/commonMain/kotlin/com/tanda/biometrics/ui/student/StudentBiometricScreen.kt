package com.tanda.biometrics.ui.student

import androidx.compose.runtime.Composable
import com.tanda.biometrics.ui.subject.SubjectBiometricUiConfig
import com.tanda.biometrics.ui.subject.SubjectScreen
import org.koin.core.scope.ScopeID

@Composable
fun StudentBiometricScreen(
    scope: ScopeID,
    onBackClick: () -> Unit = {}
) {
    SubjectScreen(
        scope = scope,
        config = SubjectBiometricUiConfig(
            subjectName = "Student",
            expectedSubjectType = "student",
            idTitle = "Student ID",
            inputDescription = "Enter student ID to capture biometrics data",
            inputHint = "Student ID",
            detailTitle = "Student detail",
            listingTitle = "Student Listing",
            searchHint = "Student name"
        ),
        onBackClick = onBackClick
    )
}
