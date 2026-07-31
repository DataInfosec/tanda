package com.tanda.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

@Composable
fun typography(): Typography {
    return Typography(
        bodyLarge = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = AppFontFamily()
        ),
        titleLarge = MaterialTheme.typography.titleLarge.copy(
            fontFamily = AppFontFamily()
        ),
        headlineLarge = MaterialTheme.typography.headlineLarge.copy(
            fontFamily = AppFontFamily()
        ))
}
