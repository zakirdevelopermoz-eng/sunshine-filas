package com.example.sunshinetotempos.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SunshineColorScheme = lightColorScheme(
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
fun SunshineTotemPOSTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SunshineColorScheme,
        typography = Typography,
        content = content
    )
}
