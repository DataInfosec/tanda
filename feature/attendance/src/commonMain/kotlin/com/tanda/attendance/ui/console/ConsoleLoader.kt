package com.tanda.attendance.ui.console

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tanda.core.ui.design.DesignText
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun ConsoleLoader() {
    Box(
        modifier = Modifier.fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) { DesignText("Loading..") }
}

@Composable
@Preview
fun PreviewConsoleLoader() {
    ConsoleLoader()
}
