package com.tanda.biometrics.ui.subject

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.tanda.biometrics.ui.capture.CaptureHeader
import com.tanda.core.ui.theme.DesignTheme
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import tanda.feature.biometrics.ui.generated.resources.Res
import tanda.feature.biometrics.ui.generated.resources.capture
import tanda.feature.biometrics.ui.generated.resources.listing

data class SubjectBiometricUiConfig(
    val subjectName: String,
    val idTitle: String = "$subjectName ID",
    val inputDescription: String = "Enter ${subjectName.lowercase()} ID to capture biometrics data",
    val inputHint: String = "$subjectName ID",
    val listingTitle: String = "$subjectName Listing",
    val searchHint: String = "$subjectName name"
)

@Composable
fun SubjectPage(
    onBackClick: () -> Unit = {},
    onContinue: () -> Unit = {},
    config: SubjectBiometricUiConfig = SubjectBiometricUiConfig(subjectName = "Staff"),
    subjectID: TextFieldState = TextFieldState(),
    isLoading: State<Boolean> = remember { mutableStateOf(false) },
    error: State<String?> = remember { mutableStateOf(null) },
    focusRequester: FocusRequester? = null,
    subjects: List<SubjectDetail> = subjectList,
    searchQuery: TextFieldState = TextFieldState(),
    onSubjectClicked: (SubjectDetail) -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(0) }
    val pageTitle = if (selectedTab == 0) config.idTitle else config.listingTitle
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding( vertical = 18.dp)
    ) {
        CaptureHeader(
            onBackClicked = onBackClick,
            text = pageTitle,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )
        SubjectSection(
            selectedTab = selectedTab,
            onSelected = {
                selectedTab = it
            },
            sections = listOf(
                stringResource(Res.string.capture),
                stringResource(Res.string.listing)
            )
        )
        when (selectedTab) {
            0 -> {
                SubjectForm(
                    title = config.idTitle,
                    description = config.inputDescription,
                    hint = config.inputHint,
                    subjectID = subjectID,
                    isLoading = isLoading,
                    error = error,
                    focusRequester = focusRequester,
                    onContinue = { onContinue() }
                )
            }
            1 -> {
                SubjectListing(
                    subjects = subjects,
                    searchQuery = searchQuery,
                    searchHint = config.searchHint,
                    onSubjectClick = onSubjectClicked
                )
            }
        }
    }
}

@Preview
@Composable
fun PreviewSubjectPage(){
    DesignTheme(darkTheme = false) {
        SubjectPage()
    }
}
