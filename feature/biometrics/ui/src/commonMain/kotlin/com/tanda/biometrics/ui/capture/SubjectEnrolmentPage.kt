package com.tanda.biometrics.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.tanda.core.ui.theme.DesignTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun SubjectEnrolmentPage(
    onBackClick: () -> Unit = {},
    onContinue: () -> Unit = {},
    subject: String = "Employee",
    subjectID: TextFieldState = TextFieldState(),
    isLoading: State<Boolean> = remember { mutableStateOf(false) },
    error: State<String?> = remember { mutableStateOf(null) },
    focusRequester: FocusRequester? = null,
    subjects: List<SubjectDetail> = subjectList,
    searchQuery: TextFieldState = TextFieldState(),
    onSubjectClicked: (SubjectDetail) -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(1) }
    var employeeId by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        BiometricCaptureHeader(
            onBackClicked = onBackClick,
            text = pageTitle
        )

        TandaTabLayout(
            selectedTab = selectedTab,
            onTabSelected = {
                selectedTab = it
            },
            tabs = listOf(
                "Capture",
                "Listing"
            )
        )

        when (selectedTab) {

            0 -> {
                updatePageTitle("Employee ID")
                SubjectInput(
                    subject = subject,
                    subjectID = subjectID,
                    isLoading = isLoading,
                    error = error,
                    focusRequester = focusRequester,
                    onContinue = { onContinue() }
                )
            }

            1 -> {
                updatePageTitle("Employee Listing")
                SubjectListing(
                    subjects = subjects,
                    searchQuery = searchQuery,
                    onSubjectClick = onSubjectClicked
                )
            }
        }
    }
}

var pageTitle = "Employee ID"
fun updatePageTitle(title: String) {
    pageTitle = title
}

@Preview
@Composable
fun PreviewSubjectEnrolmentPage(){
    DesignTheme(darkTheme = false) {
        SubjectEnrolmentPage()
    }
}
