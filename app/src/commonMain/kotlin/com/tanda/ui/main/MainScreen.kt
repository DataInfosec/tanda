package com.tanda.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanda.attendance.ui.enrollment.EnrollmentScreen
import com.tanda.core.common.interactor.LocaleInteractor
import com.tanda.core.ui.design.DesignLocale
import com.tanda.core.ui.theme.DesignTheme
import org.koin.core.scope.ScopeID
import org.koin.mp.KoinPlatform.getKoin

@Composable
fun MainScreen(scope: ScopeID) {
    val current = getKoin().getScope(scope)
    val component = remember { Main.Builder(current).build() }
    val localeInteractor = remember { component.get<LocaleInteractor>() }
    val locale = localeInteractor.observe()
        .collectAsStateWithLifecycle(localeInteractor.current())
    CompositionLocalProvider(DesignLocale provides locale) {
        DesignTheme {
            EnrollmentScreen(component.id)
        }
    }
}
