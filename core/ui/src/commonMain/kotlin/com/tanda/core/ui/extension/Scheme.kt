package com.tanda.core.ui.extension

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.tanda.core.ui.design.DesignScheme
import com.tanda.core.ui.theme.DarkDesignScheme
import com.tanda.core.ui.theme.LightDesignScheme

fun ColorScheme.isDarkTheme(): Boolean {
    return this == darkColorScheme()
}

val MaterialTheme.designScheme: DesignScheme
    @Composable
get() = if (colorScheme.isDarkTheme()) {
     LightDesignScheme
} else {
    DarkDesignScheme
}
