package com.tanda.core.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.tanda.core.ui.design.DesignScheme

val LightDesignScheme = DesignScheme(
    text = TextNeutral600,
    border = Neutral100,
    borderTint = Neutral200,
)

val DarkDesignScheme = LightDesignScheme

val LightColorScheme = lightColorScheme(
    primary = Primary500,
    onPrimary = Color.White,
    primaryContainer = Primary100,
    onPrimaryContainer = Primary900,
    inversePrimary = Primary200,
    secondary = Primary700,
    onSecondary = Color.White,
    secondaryContainer = Primary200,
    onSecondaryContainer = Primary950,
    tertiary = Accent600,
    onTertiary = Color.White,
    tertiaryContainer = Accent100,
    onTertiaryContainer = Accent900,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = NeutralBackgroundLight,
    onBackground = OnNeutralBackgroundLight,
    surface = NeutralBackgroundLight,
    onSurface = OnNeutralBackgroundLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceTint = Primary500,
    inverseSurface = Color(0xFF2D3230),
    inverseOnSurface = Color(0xFFEFF1EE),
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    scrim = Color.Black,
)

val DarkColorScheme = LightColorScheme
