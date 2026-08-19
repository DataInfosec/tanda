package com.tanda.attendance.ui.console

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tanda.core.ui.design.DesignButton
import com.tanda.core.ui.design.DesignText
import com.tanda.core.ui.design.designTertiaryButtonColors
import com.tanda.core.ui.theme.DesignTheme
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import tanda.feature.attendance.generated.resources.Res
import tanda.feature.attendance.generated.resources.checkin
import tanda.feature.attendance.generated.resources.enrol

@Composable
fun ConsolePage(
    onCheckin: () -> Unit,
    onEnrol: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(vertical = 16.dp)
    ) {
        Spacer(modifier = Modifier.weight(1f))
        DesignButton(
            onClick = onEnrol,
            modifier = Modifier.fillMaxWidth(),
        ) { DesignText(stringResource(Res.string.enrol)) }
        DesignButton(
            onClick = onCheckin,
            modifier = Modifier.fillMaxWidth()
                .padding(top = 10.dp),
//            colors = designTertiaryButtonColors(),
        ) { DesignText(stringResource(Res.string.checkin)) }
    }
}

@Preview
@Composable
fun PreviewConsolePage() {
    DesignTheme(darkTheme = false) {
        ConsolePage({}) {}
    }
}
