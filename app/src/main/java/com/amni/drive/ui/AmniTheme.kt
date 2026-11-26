package com.amni.drive.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeStore: DataStore<Preferences> by preferencesDataStore("amni_theme")

/**
 * Theme presets borrowed from Haven's 35-theme table plus driving-focused
 * accessibility presets (High Contrast, OLED Black, Night Red, Night Amber).
 */
enum class AmniPreset(val label: String, val bgHex: Long, val accent: Long, val light: Boolean) {
    // Amni-Scient family
    AMNI_DARK("Amni", 0xFF0A0A0A, 0xFF00FF9D, false),
    AMNI_LIGHT("Amni Light", 0xFFF4F4F4, 0xFF00875A, true),
    HAVEN("Haven", 0xFF191B28, 0xFF7C5CFC, false),
    AMNI_HAVEN("Amni Haven", 0xFF0A0A0A, 0xFF7C5CFC, false),
    AMNI_AI("Amni AI", 0xFF0A0A0A, 0xFFFFB74D, false),
    AMNI_CALC("Amni Calc", 0xFF0A0A0A, 0xFFFF6B35, false),
    CORE("Core", 0xFF0A0A0A, 0xFFEF5350, false),
    CRYPT("Crypt", 0xFF0A0A0A, 0xFF2979FF, false),
    EXPLORE("Explore", 0xFF0A0A0A, 0xFF00B4FF, false),

    // Driving & Accessibility
    HIGH_CONTRAST("High Contrast", 0xFF000000, 0xFFFFFF00, false),
    OLED("OLED Black", 0xFF000000, 0xFF00E5FF, false),
    NIGHT_RED("Night Red", 0xFF0A0000, 0xFFFF3B30, false),
    NIGHT_AMBER("Night Amber", 0xFF0A0700, 0xFFFFA000, false),

    // Haven community & classic themes
    ABYSS("Abyss", 0xFF040810, 0xFF1A5AFF, false),
    BLOODBORNE("Bloodborne", 0xFF0A0000, 0xFFCC0020, false),
    BRAID("Braid", 0xFF0B0D12, 0xFF00FF9D, false),
    BRAID_LIGHT("Braid Light", 0xFFF7F7F8, 0xFF00875A, true),
    CHAPEL("Chapel", 0xFF0E0C18, 0xFF8A5CF0, false),
    CRT("CRT", 0xFF080808, 0xFFFFB000, false),
    CYBERPUNK("Cyberpunk", 0xFF0A0A12, 0xFFFF2D6F, false),
    DARKSOULS("Dark Souls", 0xFF0C0A08, 0xFFC8A84E, false),
    DISCORD("Discord", 0xFF313338, 0xFF5865F2, false),
    DRACULA("Dracula", 0xFF1A1B26, 0xFFBD93F9, false),
    ELDENRING("Elden Ring", 0xFF08080E, 0xFFD4A832, false),
    FALLOUT("Fallout", 0xFF0A0E08, 0xFF14FE17, false),
    FFX("FFX", 0xFF080E1A, 0xFF40A8E0, false),
    GOSPEL("Gospel", 0xFFF5F0E8, 0xFF8B2032, true),
    HALO("Halo", 0xFF0D1A0D, 0xFFD4A020, false),
    HOLO("Holo", 0xFF000000, 0xFF33B5E5, false),
    ICE("Ice", 0xFF0A1520, 0xFF00D4FF, false),
    LOTR("LOTR", 0xFF1A1510, 0xFFD4A844, false),
    MATRIX("Matrix", 0xFF0A0A0A, 0xFF00FF41, false),
    MIDNIGHTPURPLE("Midnight", 0xFF0D0A1A, 0xFF8A5CF6, false),
    MINECRAFT("Minecraft", 0xFF1A1A1A, 0xFF55BB55, false),
    NORD("Nord", 0xFF2E3440, 0xFF88C0D0, false),
    SCRIPTURE("Scripture", 0xFF1A1610, 0xFFC8A84C, false),
    TRON("Tron", 0xFF0C141F, 0xFF6FECFF, false),
    WIN95("Win95", 0xFFBFBFBF, 0xFF000080, true),
    ZELDA("Zelda", 0xFF0A120A, 0xFF44BB44, false)
}

data class AmniPalette(
    val bg: Color,
    val panel: Color,
    val panel2: Color,
    val line: Color,
    val line2: Color,
    val acc: Color,
    val ink: Color,
    val soft: Color,
    val dim: Color,
    val gold: Color,
    val err: Color,
    val isLight: Boolean,
    val highContrast: Boolean = false,
    val cornerRadiusDp: Float = 16f,
    val fontScale: Float = 1.0f,
    val largeTargets: Boolean = false,
    val reduceMotion: Boolean = false
) {
    val accDim: Color get() = acc.copy(alpha = if (highContrast) 0.28f else if (isLight) 0.14f else 0.16f)
    val accGlow: Color get() = acc.copy(alpha = if (highContrast) 0.55f else 0.35f)
    val glassA: Color get() = if (highContrast) panel else if (isLight) panel.copy(alpha = 0.94f) else panel.copy(alpha = 0.88f)
    val glassB: Color get() = if (highContrast) bg else if (isLight) panel2.copy(alpha = 0.90f) else bg.copy(alpha = 0.82f)
    val elev: Color get() = panel2
    val scrim: Color get() = Color.Black.copy(alpha = if (highContrast) 0.78f else if (isLight) 0.28f else 0.55f)
    val hairline: Color get() = if (highContrast) line2 else if (isLight) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.10f)
    val accentBrush: Brush get() = Brush.linearGradient(listOf(acc, acc.copy(alpha = if (highContrast) 0.85f else 0.55f)))
    val surfaceBrush: Brush get() = if (highContrast) Brush.verticalGradient(listOf(panel, panel))
        else Brush.verticalGradient(listOf(panel2, panel))
    fun forCar(): AmniPalette = copy(soft = androidx.compose.ui.graphics.lerp(soft, ink, 0.55f), dim = androidx.compose.ui.graphics.lerp(dim, ink, 0.45f), line = androidx.compose.ui.graphics.lerp(line, ink, 0.15f))
}

fun paletteFor(
    preset: AmniPreset,
    accentOverride: Long?,
    highContrast: Boolean = false,
    cornerStyle: Int = 1,
    fontScale: Float = 1.0f,
    largeTargets: Boolean = false,
    reduceMotion: Boolean = false
): AmniPalette {
    val bg = Color(preset.bgHex)
    val acc = Color(accentOverride ?: preset.accent)
    val radius = when (cornerStyle) {
        0 -> 6f
        2 -> 24f
        else -> 16f
    }

    // Haven's color-mix derivation: tint surfaces and borders from (bg, accent)
    val base = if (preset.light) {
        val panel = lerp(Color.White, acc, 0.05f)
        val panel2 = lerp(bg, acc, 0.08f)
        val line = lerp(bg, Color.Black, 0.18f)
        val line2 = lerp(bg, Color.Black, 0.30f)
        AmniPalette(
            bg = bg, panel = panel, panel2 = panel2, line = line, line2 = line2,
            acc = acc, ink = Color(0xFF14161B), soft = Color(0xFF3E4450), dim = Color(0xFF646C7A),
            gold = Color(0xFFB88600), err = Color(0xFFC62D42), isLight = true
        )
    } else if (preset == AmniPreset.NIGHT_RED) {
        AmniPalette(
            bg = Color(0xFF000000), panel = Color(0xFF120404), panel2 = Color(0xFF1C0707),
            line = Color(0xFF4A1212), line2 = Color(0xFF7A1E1E),
            acc = acc, ink = Color(0xFFFF6B63), soft = Color(0xFFD94A43), dim = Color(0xFF99302B),
            gold = Color(0xFFFF6B35), err = Color(0xFFFF2D20), isLight = false
        )
    } else if (preset == AmniPreset.NIGHT_AMBER) {
        AmniPalette(
            bg = Color(0xFF000000), panel = Color(0xFF140D02), panel2 = Color(0xFF1F1404),
            line = Color(0xFF4D3208), line2 = Color(0xFF7A500D),
            acc = acc, ink = Color(0xFFFFC86B), soft = Color(0xFFD99A3B), dim = Color(0xFF94661F),
            gold = Color(0xFFFFB703), err = Color(0xFFFF5A36), isLight = false
        )
    } else {
        val panel = lerp(bg, acc, 0.07f)
        val panel2 = lerp(bg, acc, 0.12f)
        val line = lerp(bg, acc, 0.26f)
        val line2 = lerp(bg, acc, 0.38f)
        AmniPalette(
            bg = bg, panel = panel, panel2 = panel2, line = line, line2 = line2,
            acc = acc, ink = Color(0xFFEDF0F5), soft = Color(0xFFB0B7C3), dim = Color(0xFF767F8F),
            gold = Color(0xFFFFD166), err = Color(0xFFE74C5F), isLight = false,
            highContrast = preset == AmniPreset.HIGH_CONTRAST
        )
    }

    val hc = highContrast || base.highContrast
    return if (hc && !base.isLight) {
        base.copy(
            bg = Color(0xFF000000),
            panel = Color(0xFF0A0A0A),
            panel2 = Color(0xFF141414),
            line = Color(0xCCFFFFFF),
            line2 = Color(0xFFFFFFFF),
            ink = Color(0xFFFFFFFF),
            soft = Color(0xFFE8E8E8),
            dim = Color(0xFFC2C2C2),
            highContrast = true,
            cornerRadiusDp = radius,
            fontScale = fontScale,
            largeTargets = largeTargets,
            reduceMotion = reduceMotion
        )
    } else if (hc && base.isLight) {
        base.copy(
            bg = Color(0xFFFFFFFF),
            panel = Color(0xFFFFFFFF),
            panel2 = Color(0xFFF0F0F0),
            line = Color(0xFF111111),
            line2 = Color(0xFF000000),
            ink = Color(0xFF000000),
            soft = Color(0xFF1A1A1A),
            dim = Color(0xFF333333),
            highContrast = true,
            cornerRadiusDp = radius,
            fontScale = fontScale,
            largeTargets = largeTargets,
            reduceMotion = reduceMotion
        )
    } else {
        base.copy(
            cornerRadiusDp = radius,
            fontScale = fontScale,
            largeTargets = largeTargets,
            reduceMotion = reduceMotion
        )
    }
}

enum class TileLayout(val label: String, val blurb: String) {
    AUTO("Auto", "Adapt size & position to window"),
    MAP_FOCUS("Map focus", "Big map · slim side tiles"),
    MEDIA_FOCUS("Media focus", "Map + large now-playing"),
    BALANCED("Balanced", "Map · media · widgets"),
    STACKED("Stacked", "Map top · tiles bottom"),
    MAP_ONLY("Map only", "Full-bleed map")
}

data class UiTheme(
    val preset: AmniPreset = AmniPreset.AMNI_DARK,
    val accent: Long? = null,
    val weatherBg: Boolean = true,
    val tileScale: Int = 1,
    val basemap: Int = -1,
    val tileLayout: TileLayout = TileLayout.AUTO,
    val fontScale: Float = 1.0f,
    val highContrast: Boolean = false,
    val largeTargets: Boolean = false,
    val reduceMotion: Boolean = false,
    val cornerStyle: Int = 1
)

class ThemeStore(private val context: Context) {
    val theme: Flow<UiTheme> = context.themeStore.data.map {
        UiTheme(
            preset = runCatching { AmniPreset.valueOf(it[PRESET] ?: "AMNI_DARK") }.getOrDefault(AmniPreset.AMNI_DARK),
            accent = it[ACCENT]?.toLongOrNull(),
            weatherBg = it[WEATHER] ?: true,
            tileScale = it[SCALE] ?: 1,
            basemap = it[BASEMAP] ?: -1,
            tileLayout = runCatching { TileLayout.valueOf(it[LAYOUT] ?: "AUTO") }.getOrDefault(TileLayout.AUTO),
            fontScale = (it[FONT_SCALE] ?: 1.0f).coerceIn(0.85f, 1.4f),
            highContrast = it[HIGH_CONTRAST] ?: false,
            largeTargets = it[LARGE_TARGETS] ?: false,
            reduceMotion = it[REDUCE_MOTION] ?: false,
            cornerStyle = (it[CORNER_STYLE] ?: 1).coerceIn(0, 2)
        )
    }

    suspend fun setPreset(p: AmniPreset) = context.themeStore.edit { it[PRESET] = p.name; it.remove(ACCENT) }
    suspend fun setAccent(a: Long?) = context.themeStore.edit { if (a == null) it.remove(ACCENT) else it[ACCENT] = a.toString() }
    suspend fun setWeather(v: Boolean) = context.themeStore.edit { it[WEATHER] = v }
    suspend fun setScale(v: Int) = context.themeStore.edit { it[SCALE] = v }
    suspend fun setBasemap(v: Int) = context.themeStore.edit { it[BASEMAP] = v }
    suspend fun setTileLayout(v: TileLayout) = context.themeStore.edit { it[LAYOUT] = v.name }
    suspend fun setFontScale(v: Float) = context.themeStore.edit { it[FONT_SCALE] = v.coerceIn(0.85f, 1.4f) }
    suspend fun setHighContrast(v: Boolean) = context.themeStore.edit { it[HIGH_CONTRAST] = v }
    suspend fun setLargeTargets(v: Boolean) = context.themeStore.edit { it[LARGE_TARGETS] = v }
    suspend fun setReduceMotion(v: Boolean) = context.themeStore.edit { it[REDUCE_MOTION] = v }
    suspend fun setCornerStyle(v: Int) = context.themeStore.edit { it[CORNER_STYLE] = v.coerceIn(0, 2) }

    companion object {
        private val PRESET = stringPreferencesKey("preset")
        private val ACCENT = stringPreferencesKey("accent")
        private val WEATHER = booleanPreferencesKey("weather_bg")
        private val SCALE = intPreferencesKey("tile_scale")
        private val BASEMAP = intPreferencesKey("basemap")
        private val LAYOUT = stringPreferencesKey("tile_layout")
        private val FONT_SCALE = floatPreferencesKey("font_scale")
        private val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        private val LARGE_TARGETS = booleanPreferencesKey("large_targets")
        private val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        private val CORNER_STYLE = intPreferencesKey("corner_style")
    }
}

val LocalAmni = staticCompositionLocalOf { paletteFor(AmniPreset.AMNI_DARK, null) }

@Composable
fun rememberPalette(theme: UiTheme): AmniPalette = remember(theme) {
    paletteFor(
        preset = theme.preset,
        accentOverride = theme.accent,
        highContrast = theme.highContrast,
        cornerStyle = theme.cornerStyle,
        fontScale = theme.fontScale,
        largeTargets = theme.largeTargets,
        reduceMotion = theme.reduceMotion
    )
}

@Composable
fun SoftSurface(
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    elevated: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val p = LocalAmni.current
    val effectiveRadius = if (radius == 20.dp) p.cornerRadiusDp.dp else radius
    val shape = RoundedCornerShape(effectiveRadius)
    Box(
        modifier
            .then(
                if (elevated && !p.highContrast)
                    Modifier.shadow(if (p.isLight) 8.dp else 14.dp, shape, ambientColor = p.acc.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.45f))
                else Modifier
            )
            .clip(shape)
            .background(p.surfaceBrush, shape)
            .border(if (p.highContrast) 1.5.dp else 1.dp, if (p.highContrast) p.line2 else p.hairline, shape),
        content = content
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = 18.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val p = LocalAmni.current
    val effectiveRadius = if (radius == 18.dp) p.cornerRadiusDp.dp else radius
    val shape = RoundedCornerShape(effectiveRadius)
    Box(
        modifier
            .clip(shape)
            .background(if (p.highContrast) Brush.verticalGradient(listOf(p.panel, p.panel)) else Brush.verticalGradient(listOf(p.glassA, p.glassB)), shape)
            .border(if (p.highContrast) 1.5.dp else 1.dp, if (p.highContrast) p.line2 else p.line, shape),
        content = content
    )
}

@Composable
fun AccentBar(modifier: Modifier = Modifier) {
    val p = LocalAmni.current
    Box(modifier.clip(RoundedCornerShape(99.dp)).background(p.accentBrush))
}
