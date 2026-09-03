package com.tanda.biometrics.ui.staff

import androidx.compose.runtime.Composable
import com.tanda.biometrics.ui.subject.SubjectBiometricUiConfig
import com.tanda.biometrics.ui.subject.SubjectScreen
import org.koin.core.scope.ScopeID

@Composable
fun StaffBiometricScreen(
    scope: ScopeID,
    onBackClick: () -> Unit = {}
) {
    SubjectScreen(
        scope = scope,
        config = SubjectBiometricUiConfig(
            subjectName = "Staff",
            expectedSubjectType = "employee",
            idTitle = "Staff ID",
            inputDescription = "Enter staff ID to capture biometrics data",
            inputHint = "Staff ID",
            detailTitle = "Staff detail",
            listingTitle = "Staff Listing",
            searchHint = "Staff name"
        ),
        onBackClick = onBackClick
    )
}
