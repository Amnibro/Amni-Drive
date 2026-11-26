package com.amni.drive.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

@Composable
fun AmniDriveTheme(palette: AmniPalette = paletteFor(AmniPreset.AMNI_DARK, null), content: @Composable () -> Unit) {
    val curDensity = LocalDensity.current
    val scaledDensity = Density(curDensity.density, curDensity.fontScale * palette.fontScale)
    val scheme = if (palette.isLight) {
        lightColorScheme(
            primary = palette.acc,
            background = palette.bg,
            surface = palette.panel,
            surfaceVariant = palette.panel2,
            outline = palette.line2,
            onBackground = palette.ink,
            onSurface = palette.ink,
            error = palette.err
        )
    } else {
        darkColorScheme(
            primary = palette.acc,
            background = palette.bg,
            surface = palette.panel,
            surfaceVariant = palette.panel2,
            outline = palette.line2,
            onBackground = palette.ink,
            onSurface = palette.ink,
            error = palette.err
        )
    }
    CompositionLocalProvider(
        LocalAmni provides palette,
        LocalDensity provides scaledDensity
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
