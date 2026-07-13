package com.amni.drive.ui
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
private val Context.themeStore: DataStore<Preferences> by preferencesDataStore("amni_theme")
enum class AmniPreset(val label: String, val accent: Long, val light: Boolean) {
AMNI_DARK("Amni Dark", 0xFF00FF9D, false),
AMNI_LIGHT("Amni Light", 0xFF0B9B6A, true),
CRYPT("Crypt", 0xFF5BA8FF, false),
HAVEN("Haven", 0xFFA78BFA, false),
CORE("Core", 0xFFFF6B6B, false),
EXPLORE("Explore", 0xFF38BDF8, false)
}
data class AmniPalette(
val bg: Color, val panel: Color, val panel2: Color, val line: Color, val line2: Color,
val acc: Color, val ink: Color, val soft: Color, val dim: Color,
val gold: Color, val err: Color, val isLight: Boolean
) {
val accDim: Color get() = acc.copy(alpha = if (isLight) 0.14f else 0.16f)
val accGlow: Color get() = acc.copy(alpha = 0.35f)
val glassA: Color get() = if (isLight) Color(0xF2FFFFFF) else Color(0xD412151C)
val glassB: Color get() = if (isLight) Color(0xE8F4F6F8) else Color(0xB80C0E14)
val elev: Color get() = if (isLight) Color(0xFFFFFFFF) else Color(0xFF161A22)
val scrim: Color get() = Color.Black.copy(alpha = if (isLight) 0.28f else 0.55f)
val hairline: Color get() = if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.08f)
val accentBrush: Brush get() = Brush.linearGradient(listOf(acc, acc.copy(alpha = 0.55f)))
val surfaceBrush: Brush get() = if (isLight) Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF3F5F7)))
else Brush.verticalGradient(listOf(Color(0xFF181C24), Color(0xFF0E1116)))
}
fun paletteFor(preset: AmniPreset, accentOverride: Long?): AmniPalette {
val acc = Color(accentOverride ?: preset.accent)
return if (preset.light) AmniPalette(
Color(0xFFF2F4F6), Color(0xFFFFFFFF), Color(0xFFF7F8FA), Color(0xFFE2E6EB), Color(0xFFD0D5DC),
acc, Color(0xFF12151A), Color(0xFF3C4450), Color(0xFF6B7380), Color(0xFFC79600), Color(0xFFE03535), true
) else AmniPalette(
Color(0xFF07090C), Color(0xFF11151C), Color(0xFF171C25), Color(0xFF252A34), Color(0xFF323844),
acc, Color(0xFFEEF1F5), Color(0xFFA8B0BB), Color(0xFF6E7684), Color(0xFFFFD166), Color(0xFFFF5C7A), false
)
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
val tileLayout: TileLayout = TileLayout.AUTO
)
class ThemeStore(private val context: Context) {
val theme: Flow<UiTheme> = context.themeStore.data.map {
UiTheme(
runCatching { AmniPreset.valueOf(it[PRESET] ?: "AMNI_DARK") }.getOrDefault(AmniPreset.AMNI_DARK),
it[ACCENT]?.toLongOrNull(),
it[WEATHER] ?: true,
it[SCALE] ?: 1,
it[BASEMAP] ?: -1,
runCatching { TileLayout.valueOf(it[LAYOUT] ?: "AUTO") }.getOrDefault(TileLayout.AUTO)
)
}
suspend fun setPreset(p: AmniPreset) = context.themeStore.edit { it[PRESET] = p.name; it.remove(ACCENT) }
suspend fun setAccent(a: Long?) = context.themeStore.edit { if (a == null) it.remove(ACCENT) else it[ACCENT] = a.toString() }
suspend fun setWeather(v: Boolean) = context.themeStore.edit { it[WEATHER] = v }
suspend fun setScale(v: Int) = context.themeStore.edit { it[SCALE] = v }
suspend fun setBasemap(v: Int) = context.themeStore.edit { it[BASEMAP] = v }
suspend fun setTileLayout(v: TileLayout) = context.themeStore.edit { it[LAYOUT] = v.name }
companion object {
private val PRESET = stringPreferencesKey("preset")
private val ACCENT = stringPreferencesKey("accent")
private val WEATHER = booleanPreferencesKey("weather_bg")
private val SCALE = intPreferencesKey("tile_scale")
private val BASEMAP = intPreferencesKey("basemap")
private val LAYOUT = stringPreferencesKey("tile_layout")
}
}
val LocalAmni = staticCompositionLocalOf { paletteFor(AmniPreset.AMNI_DARK, null) }
@Composable
fun rememberPalette(theme: UiTheme): AmniPalette = paletteFor(theme.preset, theme.accent)
@Composable
fun SoftSurface(
modifier: Modifier = Modifier,
radius: Dp = 20.dp,
elevated: Boolean = true,
content: @Composable BoxScope.() -> Unit
) {
val p = LocalAmni.current
val shape = RoundedCornerShape(radius)
Box(
modifier
.then(if (elevated) Modifier.shadow(if (p.isLight) 8.dp else 14.dp, shape, ambientColor = p.acc.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.45f)) else Modifier)
.clip(shape)
.background(p.surfaceBrush, shape)
.border(1.dp, p.hairline, shape),
content = content
)
}
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
val p = LocalAmni.current
val shape = RoundedCornerShape(20.dp)
Box(
modifier
.shadow(12.dp, shape, ambientColor = p.acc.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.4f))
.clip(shape)
.background(Brush.verticalGradient(listOf(p.glassA, p.glassB)), shape)
.border(1.dp, Brush.verticalGradient(listOf(p.acc.copy(alpha = 0.18f), p.hairline)), shape)
) { content() }
}
@Composable
fun AccentBar(modifier: Modifier = Modifier) {
val p = LocalAmni.current
Box(modifier.clip(RoundedCornerShape(99.dp)).background(p.accentBrush))
}
