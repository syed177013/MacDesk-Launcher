package com.example.macdesk

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize

data class MacColors(
    val windowBg: Color, val titleBar: Color, val sidebar: Color, val text: Color, val secondary: Color,
    val divider: Color, val accent: Color, val selection: Color, val menuText: Color
)

val LightMacColors = MacColors(
    Color(0xFFFFFFFF), Color(0xFFE9E9EB), Color(0xFFF1F1F3), Color(0xFF1D1D1F), Color(0xFF6E6E73),
    Color(0x1F000000), Color(0xFF0A84FF), Color(0x330A84FF), Color(0xFF111111)
)
val DarkMacColors = MacColors(
    Color(0xFF1E1E20), Color(0xFF2B2B2E), Color(0xFF262629), Color(0xFFF2F2F7), Color(0xFF98989F),
    Color(0x33FFFFFF), Color(0xFF0A84FF), Color(0x550A84FF), Color.White
)

val LocalMacColors = staticCompositionLocalOf { LightMacColors }
val LocalDark = staticCompositionLocalOf { false }
val LocalRootSize = compositionLocalOf { IntSize.Zero }
/** Pre-blurred, low-res wallpaper that glass surfaces sample (no live blur => no GPU/battery cost). */
val LocalBackdrop = staticCompositionLocalOf<androidx.compose.ui.graphics.ImageBitmap?> { null }
/** Icon-pack themed replacements for built-in dock icons, keyed by AppKind name. */
val LocalBuiltinIcons = staticCompositionLocalOf<Map<String, androidx.compose.ui.graphics.ImageBitmap>> { emptyMap() }
/** The wallpaper layer, re-rendered (blurred) behind every glass surface. */
val LocalWallpaperContent = staticCompositionLocalOf<@Composable () -> Unit> { {} }

@Composable
fun MacTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkMacColors else LightMacColors
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(primary = c.accent) else lightColorScheme(primary = c.accent)
    ) {
        CompositionLocalProvider(LocalMacColors provides c, LocalDark provides dark, content = content)
    }
}
