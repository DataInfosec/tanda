package com.tanda.core.ui.design

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.tanda.core.ui.theme.DesignTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun DesignTextField(
    modifier: Modifier = Modifier,
    state: TextFieldState = remember { TextFieldState() },
    enabled: Boolean = true,
    readOnly: Boolean = false,
    hasError: Boolean = false,
    hint: String? = null,
    focusRequester: FocusRequester? = null,
    color: Color = MaterialTheme.colorScheme.surfaceTint,
    unFocusedColor: Color = MaterialTheme.colorScheme.outline,
    hintColor: Color = MaterialTheme.colorScheme.outlineVariant,
    contentAlignment: Alignment = Alignment.TopStart,
    unFocusedHintColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    unFocusedContentColor: Color = MaterialTheme.colorScheme.onSurface,
    unFocusedContainerColor: Color = MaterialTheme.colorScheme.surface,
    errorColor: Color = MaterialTheme.colorScheme.error,
    errorBackground: Color = MaterialTheme.colorScheme.surface,
    errorBorder: Color = MaterialTheme.colorScheme.errorContainer,
    animationSpec: FiniteAnimationSpec<Float> = tween(),
    shape: Shape = RoundedCornerShape(12.dp),
    contentPadding: PaddingValues = PaddingValues(
        horizontal = 10.dp,
        vertical = 16.dp
    ),
    inputTransformation: InputTransformation? = null,
    fontWeight: FontWeight = FontWeight.SemiBold,
    textAlign: TextAlign = TextAlign.Start,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    onTextLayout: (Density.(getResult: () -> TextLayoutResult?) -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    cursorBrush: Brush = SolidColor(MaterialTheme.colorScheme.primary),
    outputTransformation: OutputTransformation? = null,
    decorator: TextFieldDecorator? = null,
    durationMillis: Int = 10,
    delayMillis: Int = 0,
    easing: Easing = FastOutSlowInEasing,
    scrollState: ScrollState = rememberScrollState(),
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    var isFocused by remember { mutableStateOf(false) }
    val updatedLeading by rememberUpdatedState(leading)
    val updatedTrailing by rememberUpdatedState(trailing)
    val borderColor by animateColorAsState(
        targetValue = if (hasError) {
            errorBorder
        } else if (isFocused && enabled) {
            color
        } else {
            unFocusedColor
        },
        animationSpec = tween(
            durationMillis = durationMillis,
            delayMillis = delayMillis,
            easing = easing
        )
    )
    val contentColor by animateColorAsState(
        targetValue = if (isFocused && enabled) {
            contentColor
        } else {
            unFocusedContentColor
        },
        animationSpec = tween(
            durationMillis = durationMillis,
            delayMillis = delayMillis,
            easing = easing
        )
    )
    val hintContentColor by animateColorAsState(
        targetValue = if (hasError) {
            errorColor
        } else if (isFocused) {
            hintColor
        } else {
            unFocusedHintColor
        },
        animationSpec = tween(
            durationMillis = durationMillis,
            delayMillis = delayMillis,
            easing = easing
        )
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (hasError) {
            errorBackground
        } else if (isFocused) {
            containerColor
        } else {
            unFocusedContainerColor
        },
        animationSpec = tween(
            durationMillis = durationMillis,
            delayMillis = delayMillis,
            easing = easing
        )
    )
    Box(
        modifier = modifier.clip(shape)
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = shape
            ).padding(1.dp)
            .padding(contentPadding)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides textStyle.copy(
                color = contentColor,
                fontWeight = fontWeight
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                updatedLeading()
                Box(contentAlignment = contentAlignment) {
                    BasicTextField(
                        state = state,
                        enabled = enabled,
                        readOnly = readOnly,
                        inputTransformation = inputTransformation,
                        textStyle = LocalTextStyle.current.copy(
                            textAlign = textAlign
                        ),
                        keyboardOptions = keyboardOptions,
                        onKeyboardAction = onKeyboardAction,
                        lineLimits = lineLimits,
                        onTextLayout = onTextLayout,
                        interactionSource = interactionSource,
                        cursorBrush = cursorBrush,
                        outputTransformation = outputTransformation,
                        decorator = decorator,
                        scrollState = scrollState,
                        modifier = Modifier.then(
                            if (focusRequester != null) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier
                            }
                        )
                    )
                    Crossfade(
                        targetState = state.text.isEmpty(),
                        animationSpec = animationSpec
                    ) { targetState ->
                        if (targetState) {
                            Text(
                                text = hint ?: "",
                                style = LocalTextStyle.current.copy(
                                    textAlign = textAlign
                                ),
                                color = hintContentColor
                            )
                        }
                    }
                }
                updatedTrailing()
            }
        }
    }
}

@Preview
@Composable
fun PreviewDesignInput() {
    DesignTheme(darkTheme = true) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DesignTextField(
                hint = "0",
                state = remember { TextFieldState() },
                textAlign = TextAlign.Center,
                textStyle = MaterialTheme.typography.bodyMedium,
                contentAlignment = Alignment.Center,
                modifier = Modifier.width(56.dp)
            )
            DesignTextField(
                hint = "0",
                state = remember { TextFieldState() },
                modifier = Modifier.fillMaxWidth(),
            )
            DesignTextField(
                hint = "0",
                hasError = true,
                state = remember { TextFieldState() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
