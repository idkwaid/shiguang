package dev.lumo.gallery.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = LightError
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = DarkError
)

/**
 * 当前是否深色，供需要“纯黑沉浸”的页面（全屏查看器）使用。
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
fun ShiguangTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colors,
            typography = ShiguangTypography,
            content = content
        )
    }
}

/**
 * 动态取色主题：全屏查看器根据当前图片主色生成沉浸底色。
 */
@Composable
fun ShiguangAccentTheme(
    accent: Color?,
    content: @Composable () -> Unit
) {
    val dark = LocalIsDarkTheme.current
    val colors = when {
        accent == null -> if (dark) DarkColors else LightColors
        dark -> darkColorScheme(
            primary = accent,
            onPrimary = DarkOnPrimary,
            background = DarkBackground,
            onBackground = DarkOnSurface,
            surface = DarkSurface,
            onSurface = DarkOnSurface,
            surfaceVariant = accent.copy(alpha = 0.18f),
            onSurfaceVariant = DarkOnSurfaceVariant,
            outline = DarkOutline
        )
        else -> lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            background = LightBackground,
            onBackground = LightOnSurface,
            surface = LightSurface,
            onSurface = LightOnSurface,
            surfaceVariant = accent.copy(alpha = 0.12f),
            onSurfaceVariant = LightOnSurfaceVariant,
            outline = LightOutline
        )
    }
    MaterialTheme(colorScheme = colors, typography = ShiguangTypography, content = content)
}

@Composable
@ReadOnlyComposable
fun currentIsDark(): Boolean = LocalIsDarkTheme.current
