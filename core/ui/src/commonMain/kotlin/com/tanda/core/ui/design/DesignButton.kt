package com.tanda.core.ui.design

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tanda.core.ui.theme.DesignTheme
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import tanda.core.ui.generated.resources.Res
import tanda.core.ui.generated.resources.loading

data class DesignButtonColors(
    val containerColor: Color,
    val contentColor: Color,
    val disabledContainerColor: Color,
    val disabledContentColor: Color
)

@Composable
fun designPrimaryButtonColors(): DesignButtonColors {
    return DesignButtonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceDim,
        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun designSecondaryButtonColors(): DesignButtonColors {
    return DesignButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceDim,
        contentColor = MaterialTheme.colorScheme.onBackground,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceDim,
        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun designTertiaryButtonColors(): DesignButtonColors {
    return DesignButtonColors(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        disabledContainerColor = MaterialTheme.colorScheme.background,
        disabledContentColor = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
fun designDangerButtonColors(): DesignButtonColors {
    return DesignButtonColors(
        containerColor = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onSecondary,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceDim,
        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun DesignButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    feedbackType: HapticFeedbackType? = HapticFeedbackType.KeyboardTap,
    shape: Shape = RoundedCornerShape(10.dp),
    fontWeight: FontWeight = FontWeight.Bold,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    colors: DesignButtonColors = designPrimaryButtonColors(),
    elevation: Dp = 0.dp,
    border: BorderStroke? = null,
    minHeight: Dp = 48.dp,
    minWidth: Dp = 64.dp,
    durationMillis: Int = 1000,
    easing: Easing = FastOutSlowInEasing,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    loading: @Composable () -> Unit = { DesignLoader(style) },
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val clickHandler by rememberUpdatedState(onClick)
    val updatedLoading by rememberUpdatedState(loading)
    val updatedContent by rememberUpdatedState(content)
    val contentColor = if (enabled) colors.contentColor else colors.disabledContentColor
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (enabled) {
                colors.containerColor
            } else {
                colors.disabledContainerColor
            })
            .then(if (border != null) {
                Modifier.border(border, shape)
            } else {
                Modifier
            }).shadow(elevation = elevation, shape = shape)
            .clickable(
                role = Role.Button,
                enabled = enabled && !isLoading,
                onClick = { if (!isLoading && enabled) {
                    feedbackType?.let { haptic.performHapticFeedback(it) }
                    clickHandler()
                } }
            ).defaultMinSize(minWidth = minWidth, minHeight = minHeight),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition()
            val alpha by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0.6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis, easing = easing),
                    repeatMode = RepeatMode.Reverse
                )
            )
            Box(
                modifier = Modifier.graphicsLayer {
                    this.alpha = if (isLoading) alpha else 0f },
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides contentColor,
                    LocalTextStyle provides style.copy(
                        color = contentColor,
                        fontWeight = fontWeight
                    )
                ) { updatedLoading() }
            }
            Box(modifier = Modifier.graphicsLayer {
                this.alpha = if (!isLoading) 1f else 0f }) {
                CompositionLocalProvider(
                    LocalContentColor provides contentColor,
                    LocalTextStyle provides style.copy(
                        fontWeight = fontWeight
                    )
                ) { updatedContent() }
            }
        }
    }
}

@Composable
fun DesignLoader(style: TextStyle = MaterialTheme.typography.bodyMedium) {
    DesignText(stringResource(Res.string.loading), style = style)
}

@Preview
@Composable
fun PreviewDesignButton() {
    DesignTheme(darkTheme = false) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            DesignButton(
                onClick = {},
            ) { DesignText("Hello, world!") }
            DesignButton(
                onClick = {},
                isLoading = true,
                loading = { DesignText("Loading...") }
            ) { DesignText("Hello, world!") }
            DesignButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.sizeIn(minHeight = 56.dp, minWidth = 200.dp)
            ) { DesignText("Large Button") }
            DesignButton(
                onClick = {},
                colors = designSecondaryButtonColors(),
            ) { DesignText("Hello, world!") }
            DesignButton(
                onClick = {},
                isLoading = true,
                colors = designSecondaryButtonColors(),
                loading = { DesignText("Loading...") }
            ) { DesignText("Hello, world!") }
            DesignButton(
                onClick = {},
                enabled = false,
                colors = designSecondaryButtonColors(),
                modifier = Modifier.sizeIn(minHeight = 56.dp, minWidth = 200.dp)
            ) { DesignText("Large Button") }
            DesignButton(
                onClick = {},
                colors = designTertiaryButtonColors(),
            ) { DesignText("Hello, world!") }
            DesignButton(
                onClick = {},
                enabled = false,
                colors = designTertiaryButtonColors(),
            ) { DesignText("Hello, world!") }
        }
    }
}
