package com.tanda.biometrics.ui.staff

import androidx.compose.runtime.Composable
import com.tanda.biometrics.ui.capture.SubjectBiometricScreen
import com.tanda.biometrics.ui.capture.SubjectBiometricUiConfig
import org.jetbrains.compose.resources.stringResource
import org.koin.core.scope.ScopeID
import tanda.feature.biometrics.ui.generated.resources.Res
import tanda.feature.biometrics.ui.generated.resources.enter_staff_id
import tanda.feature.biometrics.ui.generated.resources.staff
import tanda.feature.biometrics.ui.generated.resources.staff_detail
import tanda.feature.biometrics.ui.generated.resources.staff_id
import tanda.feature.biometrics.ui.generated.resources.staff_listing
import tanda.feature.biometrics.ui.generated.resources.staff_name

@Composable
fun StaffBiometricScreen(
    scope: ScopeID,
    deviceId: Int,
    deviceIndex: Int = 0,
    onBackClick: () -> Unit = {}
) {
    SubjectBiometricScreen(
        scope = scope,
        config = SubjectBiometricUiConfig(
            subjectName = stringResource(Res.string.staff),
            expectedSubjectType = "employee",
            idTitle = stringResource(Res.string.staff_id),
            inputDescription = stringResource(Res.string.enter_staff_id),
            inputHint = stringResource(Res.string.staff_id),
            detailTitle = stringResource(Res.string.staff_detail),
            listingTitle = stringResource(Res.string.staff_listing),
            searchHint = stringResource(Res.string.staff_name)
        ),
        deviceId = deviceId,
        deviceIndex = deviceIndex,
        onBackClick = onBackClick
    )
}
