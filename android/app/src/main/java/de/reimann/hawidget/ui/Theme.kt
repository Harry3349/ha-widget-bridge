package de.reimann.hawidget.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HaColors = darkColorScheme(
    primary = Color(0xFF00E676),
    onPrimary = Color(0xFF00210C),
    secondary = Color(0xFF4DD0E1),
    onSecondary = Color(0xFF00202A),
    background = Color(0xFF0E0E14),
    onBackground = Color(0xFFEDEDF2),
    surface = Color(0xFF16161F),
    onSurface = Color(0xFFEDEDF2),
    surfaceVariant = Color(0xFF23232F),
    onSurfaceVariant = Color(0xFFB9B9C6),
    error = Color(0xFFFF5252),
    onError = Color(0xFF2A0000),
    outline = Color(0xFF44445A),
)

@Composable
fun HaWidgetBridgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HaColors, content = content)
}

/** Bildschirme der App (bewusst ohne Navigations-Bibliothek). */
enum class Screen {
    WIDGETS,
    EDITOR,
    SETUP,
}
