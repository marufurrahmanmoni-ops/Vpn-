package com.moni.freeglobalvpn.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.moni.freeglobalvpn.data.ThemeMode

object Brand {
    val Violet = Color(0xFF6C5CE7)
    val Cyan = Color(0xFF00C2FF)
    val Green = Color(0xFF22C55E)
    val Amber = Color(0xFFF59E0B)
    val Red = Color(0xFFEF4444)
    val Slate = Color(0xFF64748B)
    val gradient = Brush.linearGradient(listOf(Violet, Cyan))
    val connectedGradient = Brush.linearGradient(listOf(Color(0xFF16A34A), Color(0xFF22D3EE)))
    val connectingGradient = Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFF97316)))
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8B7CFF),
    onPrimary = Color.White,
    secondary = Brand.Cyan,
    background = Color(0xFF0A0E1A),
    onBackground = Color(0xFFE6E9F2),
    surface = Color(0xFF121829),
    onSurface = Color(0xFFE6E9F2),
    surfaceVariant = Color(0xFF1B2236),
    onSurfaceVariant = Color(0xFF9AA4BF),
    surfaceContainer = Color(0xFF151C2E),
    surfaceContainerHigh = Color(0xFF1B2236),
    outline = Color(0xFF2A3350),
    error = Brand.Red,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B4BDB),
    onPrimary = Color.White,
    secondary = Color(0xFF0095C8),
    background = Color(0xFFF5F7FC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEDF0F8),
    onSurfaceVariant = Color(0xFF5B6478),
    surfaceContainer = Color(0xFFF0F3FA),
    surfaceContainerHigh = Color(0xFFE8ECF6),
    outline = Color(0xFFD6DCEA),
    error = Color(0xFFDC2626),
)

private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun FreeVpnTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val colors = if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            @Suppress("DEPRECATION")
            window.navigationBarColor = colors.surface.toArgb()
        }
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
