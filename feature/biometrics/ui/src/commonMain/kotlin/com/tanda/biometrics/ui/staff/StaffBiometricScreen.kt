package com.tanda.biometrics.ui.staff

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.tanda.biometrics.ui.subject.SubjectBiometricUiConfig
import com.tanda.biometrics.ui.subject.SubjectPage

@Composable
fun StaffBiometricScreen(
    onBackClick: () -> Unit = {}
) {
    SubjectPage(
        config = SubjectBiometricUiConfig(
            subjectName = "Staff",
            idTitle = "Staff ID",
            inputDescription = "Enter staff ID to capture biometrics data",
            inputHint = "Staff ID",
            listingTitle = "Staff Listing",
            searchHint = "Staff name"
        ),
        subjectID = remember { TextFieldState() },
        searchQuery = remember { TextFieldState() },
        isLoading = remember { mutableStateOf(false) },
        error = remember { mutableStateOf(null) },
        onBackClick = onBackClick
    )
}
