package com.tanda.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tanda.core.ui.design.DesignButton
import com.tanda.core.ui.design.DesignText
import com.tanda.core.ui.theme.DesignTheme
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import tanda.app.generated.resources.Res
import tanda.app.generated.resources.attendance

@Composable
fun MainPage(onClick: () -> Unit) {
    val handleClick by rememberUpdatedState(onClick)
    Box(
        modifier = Modifier.fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        DesignButton(
            onClick = { handleClick() },
            modifier = Modifier.fillMaxWidth(),
        ) { DesignText(stringResource(Res.string.attendance)) }
    }
}

@Preview
@Composable
fun PreviewMainPage() {
    DesignTheme(darkTheme = false) {
        MainPage {}
    }
}
