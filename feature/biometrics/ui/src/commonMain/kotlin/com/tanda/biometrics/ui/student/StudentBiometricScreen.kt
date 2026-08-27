package com.tanda.biometrics.ui.student

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.tanda.biometrics.ui.capture.SubjectBiometricUiConfig
import com.tanda.biometrics.ui.capture.CaptureSubject

@Composable
fun StudentBiometricScreen(
    onBackClick: () -> Unit = {}
) {
    CaptureSubject(
        config = SubjectBiometricUiConfig(
            subjectName = "Student",
            idTitle = "Student ID",
            inputDescription = "Enter student ID to capture biometrics data",
            inputHint = "Student ID",
            listingTitle = "Student Listing",
            searchHint = "Student name"
        ),
        subjectID = remember { TextFieldState() },
        searchQuery = remember { TextFieldState() },
        isLoading = remember { mutableStateOf(false) },
        error = remember { mutableStateOf(null) },
        onBackClick = onBackClick
    )
}
