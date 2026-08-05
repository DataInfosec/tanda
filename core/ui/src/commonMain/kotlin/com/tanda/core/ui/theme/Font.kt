package com.tanda.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import tanda.core.ui.generated.resources.Res
import tanda.core.ui.generated.resources.inter_bold
import tanda.core.ui.generated.resources.inter_regular
import tanda.core.ui.generated.resources.inter_semi_bold

@Composable
fun defaultFontFamily() = FontFamily(
    Font(Res.font.inter_regular, weight = FontWeight.Normal),
    Font(Res.font.inter_semi_bold, weight = FontWeight.SemiBold),
    Font(Res.font.inter_bold, weight = FontWeight.Bold),
)
