package com.amni.drive.ui
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
@Composable
fun AmniDriveTheme(palette: AmniPalette = paletteFor(AmniPreset.AMNI_DARK, null), content: @Composable () -> Unit) {
    val scheme = if (palette.isLight) lightColorScheme(primary = palette.acc, background = palette.bg, surface = palette.panel, onBackground = palette.ink, onSurface = palette.ink)
    else darkColorScheme(primary = palette.acc, background = palette.bg, surface = palette.panel, onBackground = palette.ink, onSurface = palette.ink)
    CompositionLocalProvider(LocalAmni provides palette) { MaterialTheme(colorScheme = scheme, content = content) }
}
