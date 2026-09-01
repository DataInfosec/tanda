package com.tanda.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tanda.core.ui.design.DesignText
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.scope.ScopeID
import tanda.app.generated.resources.Res
import tanda.app.generated.resources.splash_image
import tanda.app.generated.resources.splash_screen_image

@Composable
fun SplashScreen(
    scope: ScopeID,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(Res.drawable.splash_image),
            contentDescription = stringResource(
                Res.string.splash_screen_image
            )
        )
    }
}
