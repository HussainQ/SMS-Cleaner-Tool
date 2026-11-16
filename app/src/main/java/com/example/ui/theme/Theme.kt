package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val LocalCleanSmsThemeManaged = staticCompositionLocalOf { false }
val LocalUseDeviceMaterialTheme = staticCompositionLocalOf { false }

private val DarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = Navy900,
    primaryContainer = Navy800,
    onPrimaryContainer = CyanAccent,
    secondary = TealAccent,
    onSecondary = Navy900,
    secondaryContainer = Navy700,
    onSecondaryContainer = TealAccent,
    tertiary = PurpleAccent,
    onTertiary = Color.White,
    background = SurfaceDark,
    surface = CardDark,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = CardBorder,
    onSurfaceVariant = TextSecondary,
    outline = CardBorder,
    error = RedDestructive,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF0D9488),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF115E59),
    tertiary = PurpleAccent,
    onTertiary = Color.White,
    background = SurfaceLight,
    surface = CardLight,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = RedDestructive,
    onError = Color.White
)

@Composable
fun CleanSMSTheme(
    useDeviceMaterialTheme: Boolean = false,
    darkTheme: Boolean = if (useDeviceMaterialTheme) isSystemInDarkTheme() else true,
    dynamicColor: Boolean = useDeviceMaterialTheme,
    themeManaged: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(
        LocalCleanSmsThemeManaged provides themeManaged,
        LocalUseDeviceMaterialTheme provides useDeviceMaterialTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
