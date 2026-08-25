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
import com.tanda.biometrics.domain.exception.ScannerException
import com.tanda.core.ui.design.DesignText
import com.tanda.core.ui.theme.DesignTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun ConsoleError(error: Throwable) {
    Box(
        modifier = Modifier.fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (error is ScannerException) {
            DesignText("Error: ${error.message}")
        } else {
            DesignText("Error: ${(error.cause ?: error)::class.simpleName}")
        }
    }
}

@Preview
@Composable
fun PreviewConsoleError(){
    DesignTheme(darkTheme = false){
        ConsoleError(ScannerException("Scanner not found"))
    }
}
