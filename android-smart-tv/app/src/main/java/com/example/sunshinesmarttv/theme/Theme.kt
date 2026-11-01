package com.example.sunshinesmarttv.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SunshineTvScheme = lightColorScheme(
    primary = SunshineRed,
    onPrimary = Color.White,
    primaryContainer = SunshineRedContainer,
    onPrimaryContainer = SunshineRedDark,
    secondary = SunshineGreen,
    onSecondary = Color.White,
    secondaryContainer = SunshineGreenContainer,
    onSecondaryContainer = SunshineGreen,
    background = SunshineSurface,
    surface = SunshineCard,
    onBackground = SunshineTextPrimary,
    onSurface = SunshineTextPrimary
)

@Composable
fun SunshineSmartTVTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SunshineTvScheme,
        typography = Typography,
        content = content
    )
}
