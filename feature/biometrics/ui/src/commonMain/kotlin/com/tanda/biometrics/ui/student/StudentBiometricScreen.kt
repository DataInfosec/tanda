package com.tanda.biometrics.ui.student

import androidx.compose.runtime.Composable
import com.tanda.biometrics.ui.capture.SubjectBiometricScreen
import com.tanda.biometrics.ui.capture.SubjectBiometricUiConfig
import org.jetbrains.compose.resources.stringResource
import org.koin.core.scope.ScopeID
import tanda.feature.biometrics.ui.generated.resources.Res
import tanda.feature.biometrics.ui.generated.resources.enter_student_id
import tanda.feature.biometrics.ui.generated.resources.student
import tanda.feature.biometrics.ui.generated.resources.student_detail
import tanda.feature.biometrics.ui.generated.resources.student_id
import tanda.feature.biometrics.ui.generated.resources.student_listing
import tanda.feature.biometrics.ui.generated.resources.student_name

@Composable
fun StudentBiometricScreen(
    scope: ScopeID,
    deviceId: Int,
    deviceIndex: Int = 0,
    onBackClick: () -> Unit = {}
) {
    SubjectBiometricScreen(
        scope = scope,
        config = SubjectBiometricUiConfig(
            subjectName = stringResource(Res.string.student),
            expectedSubjectType = "student",
            idTitle = stringResource(Res.string.student_id),
            inputDescription = stringResource(Res.string.enter_student_id),
            inputHint = stringResource(Res.string.student_id),
            detailTitle = stringResource(Res.string.student_detail),
            listingTitle = stringResource(Res.string.student_listing),
            searchHint = stringResource(Res.string.student_name)
        ),
        deviceId = deviceId,
        deviceIndex = deviceIndex,
        onBackClick = onBackClick
    )
}
