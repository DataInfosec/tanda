package com.tanda.core.ui.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

const val DEFAULT_DURATION = 250

@Composable
fun<S> DesignMotion(
    targetState: S,
    modifier: Modifier = Modifier,
    content: @Composable() AnimatedContentScope.(targetState: S) -> Unit
) {
    AnimatedContent(
        targetState = targetState,
        contentKey = { it },
        modifier = modifier,
        transitionSpec = {
            (slideInVertically(
                initialOffsetY = { (it * 0.38f).toInt() },
                animationSpec = tween(DEFAULT_DURATION, easing = FastOutSlowInEasing)
            ) + fadeIn(
                animationSpec = tween(DEFAULT_DURATION, delayMillis = 60)
            ) + scaleIn(
                initialScale = 0.95f,
                animationSpec = tween(DEFAULT_DURATION, easing = FastOutSlowInEasing)
            )) togetherWith
                    (slideOutVertically(
                        targetOffsetY = { -(it * 0.22f).toInt() },
                        animationSpec = tween(DEFAULT_DURATION, easing = FastOutSlowInEasing)
                    ) + fadeOut(
                        animationSpec = tween((DEFAULT_DURATION * 0.85f).toInt())
                    ) + scaleOut(
                        targetScale = 0.97f,
                        animationSpec = tween(DEFAULT_DURATION)
                    ))
        },
        content = content
    )
}
