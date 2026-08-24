package com.classcue.kexing.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF315DA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF0B2E60),
    secondary = Color(0xFF53637B),
    secondaryContainer = Color(0xFFD7E3F8),
    tertiary = Color(0xFF006C51),
    tertiaryContainer = Color(0xFF85F8CC),
    error = Color(0xFFBA1A1A),
    surface = Color(0xFFF9F9FF),
    surfaceContainer = Color(0xFFEDEEF5),
    surfaceContainerLow = Color(0xFFF3F3FA),
    outline = Color(0xFF74777F)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF16477F),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFFBBC7DB),
    secondaryContainer = Color(0xFF3C485A),
    tertiary = Color(0xFF66DBB1),
    tertiaryContainer = Color(0xFF00513C),
    error = Color(0xFFFFB4AB),
    surface = Color(0xFF111318),
    surfaceContainer = Color(0xFF1D2026),
    surfaceContainerLow = Color(0xFF191C21),
    outline = Color(0xFF8E9099)
)

@Composable
fun ClassCueTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) DarkColors else LightColors
    MaterialTheme(colorScheme = scheme, content = content)
}
