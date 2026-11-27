package com.amni.drive.ui
import android.content.Context
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amni.drive.launcher.CarEmbed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
data class AmniMapPrefs(
    val themeMode: String = "follow",
    val look: String = "auto",
    val vehicle: String = "sedan",
    val paintHex: Long = 0xFFC89B4EL,
    val perspective3d: Boolean = true,
    val headingUp: Boolean = true,
    val buildings3d: Boolean = true,
    val trees3d: Boolean = true,
    val bridges3d: Boolean = true,
    val terrain: Boolean = true,
    val traffic: Boolean = true,
    val avoidHighways: Boolean = false,
    val avoidTolls: Boolean = false,
    val voiceMode: String = "all"
) {
    fun effectiveTheme(hostIsLight: Boolean): String = when (themeMode) {
        "light" -> "light"
        "dark" -> "dark"
        "sunset" -> "sunset"
        else -> if (hostIsLight) "light" else "dark"
    }
    fun toBundle(hostIsLight: Boolean): Bundle = Bundle().apply {
        putString("theme", effectiveTheme(hostIsLight))
        putString("look", look)
        putString("vehicle", vehicle)
        putLong("paint", paintHex)
        putBoolean("tilt", perspective3d)
        putBoolean("north", !headingUp)
        putBoolean("buildings3d", buildings3d)
        putBoolean("trees3d", trees3d)
        putBoolean("bridges3d", bridges3d)
        putBoolean("relief", terrain)
        putBoolean("traffic", traffic)
        putBoolean("noHw", avoidHighways)
        putBoolean("noToll", avoidTolls)
        putBoolean("mute", voiceMode == "mute")
        if (voiceMode != "mute") putString("voiceMode", voiceMode)
    }
}
object AmniMapStudio {
    private const val PREF = "amni_map_studio"
    val LOOKS = listOf(
        "auto" to "Auto",
        "night" to "Night",
        "citrus" to "Street",
        "cotton" to "Ink",
        "forest" to "Topo",
        "sat" to "Satellite",
        "gold" to "Amni Gold",
        "neon" to "Neon",
        "blueprint" to "Blueprint",
        "paper" to "Paper",
        "contrast" to "Contrast"
    )
    val VEHICLES = listOf(
        "sedan" to "🚗 Sedan",
        "race" to "🏎️ Racer",
        "family" to "🚙 SUV",
        "pickup" to "🛻 Pickup",
        "ev" to "⚡ EV Hatch",
        "minivan" to "🚐 Van",
        "classic" to "🚘 Classic",
        "suv" to "✨ Luxury SUV",
        "hatch" to "🏁 Hot Hatch",
        "formula" to "🏆 Formula",
        "future" to "🚀 Future",
        "moto" to "🏍️ Moto",
        "taxi" to "🚕 Taxi",
        "police" to "🚓 Police",
        "monster" to "🦖 Monster",
        "speeder" to "🛸 Speeder",
        "rover" to "🌕 Rover"
    )
    val PAINTS = listOf(
        0xFFD7261EL to "Red",
        0xFFC89B4EL to "Gold",
        0xFF1B2A4AL to "Midnight",
        0xFFEDEFF2L to "White",
        0xFF3A3F47L to "Graphite",
        0xFF1F4D3AL to "Green",
        0xFF1AA3A8L to "Teal",
        0xFFF28C28L to "Sunset",
        0xFF2456C9L to "Blue",
        0xFF6B2D6BL to "Plum",
        0xFF9BD13AL to "Lime",
        0xFFE75A9AL to "Pink"
    )
    private val _prefs = MutableStateFlow(AmniMapPrefs())
    val prefs: StateFlow<AmniMapPrefs> = _prefs.asStateFlow()
    @Volatile private var loaded = false
    fun ensureLoaded(ctx: Context): AmniMapPrefs {
        if (!loaded) {
            val sp = ctx.applicationContext.getSharedPreferences(PREF, 0)
            _prefs.value = AmniMapPrefs(
                themeMode = sp.getString("themeMode", "follow") ?: "follow",
                look = sp.getString("look", "auto") ?: "auto",
                vehicle = sp.getString("vehicle", "sedan") ?: "sedan",
                paintHex = sp.getLong("paintHex", 0xFFC89B4EL),
                perspective3d = sp.getBoolean("perspective3d", true),
                headingUp = sp.getBoolean("headingUp", true),
                buildings3d = sp.getBoolean("buildings3d", true),
                trees3d = sp.getBoolean("trees3d", true),
                bridges3d = sp.getBoolean("bridges3d", true),
                terrain = sp.getBoolean("terrain", true),
                traffic = sp.getBoolean("traffic", true),
                avoidHighways = sp.getBoolean("avoidHighways", false),
                avoidTolls = sp.getBoolean("avoidTolls", false),
                voiceMode = sp.getString("voiceMode", "all") ?: "all"
            )
            loaded = true
        }
        return _prefs.value
    }
    fun update(ctx: Context, hostIsLight: Boolean, transform: (AmniMapPrefs) -> AmniMapPrefs) {
        val next = transform(ensureLoaded(ctx))
        _prefs.value = next
        ctx.applicationContext.getSharedPreferences(PREF, 0).edit()
            .putString("themeMode", next.themeMode)
            .putString("look", next.look)
            .putString("vehicle", next.vehicle)
            .putLong("paintHex", next.paintHex)
            .putBoolean("perspective3d", next.perspective3d)
            .putBoolean("headingUp", next.headingUp)
            .putBoolean("buildings3d", next.buildings3d)
            .putBoolean("trees3d", next.trees3d)
            .putBoolean("bridges3d", next.bridges3d)
            .putBoolean("terrain", next.terrain)
            .putBoolean("traffic", next.traffic)
            .putBoolean("avoidHighways", next.avoidHighways)
            .putBoolean("avoidTolls", next.avoidTolls)
            .putString("voiceMode", next.voiceMode)
            .apply()
        CarEmbed.sendConfig(next.toBundle(hostIsLight))
    }
    fun syncHostTheme(ctx: Context, hostIsLight: Boolean) {
        val cur = ensureLoaded(ctx)
        CarEmbed.sendConfig(cur.toBundle(hostIsLight))
    }
}
@Composable
fun AmniMapStudioControls(ctx: Context, prefs: AmniMapPrefs, hostIsLight: Boolean, compact: Boolean = false) {
    val p = LocalAmni.current
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp)) {
        Text("MAP THEME SYNC", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("follow" to "Follow Amni-Drive (${if (hostIsLight) "Light" else "Dark"})", "light" to "Always Light", "dark" to "Always Dark", "sunset" to "Sunset Auto").forEach { (id, lbl) ->
                val sel = prefs.themeMode == id
                Box(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (sel) p.acc else p.panel2)
                        .border(1.dp, if (sel) p.acc else p.hairline, RoundedCornerShape(9.dp))
                        .clickable { AmniMapStudio.update(ctx, hostIsLight) { it.copy(themeMode = id) } }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(lbl, color = if (sel) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text("3D WORLD & CAMERA ORIENTATION", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
        StudioSwitchRow("🧭 Perspective Top-Dead-Center", "Heading-up 3D view ahead instead of North-up", prefs.headingUp) {
            AmniMapStudio.update(ctx, hostIsLight) { s -> s.copy(headingUp = it) }
        }
        StudioSwitchRow("📐 3D Camera Tilt", "Tilts horizon forward in both free-drive & routing", prefs.perspective3d) {
            AmniMapStudio.update(ctx, hostIsLight) { s -> s.copy(perspective3d = it) }
        }
        StudioSwitchRow("🏙️ 3D Buildings", "Extruded city skylines & landmarks", prefs.buildings3d) {
            AmniMapStudio.update(ctx, hostIsLight) { s -> s.copy(buildings3d = it) }
        }
        StudioSwitchRow("🌲 3D Trees & Canopy", "Extruded woodlands, parks & street tree crowns", prefs.trees3d) {
            AmniMapStudio.update(ctx, hostIsLight) { s -> s.copy(trees3d = it) }
        }
        StudioSwitchRow("🌉 3D Bridges & Overpasses", "Raised multi-deck highway spans & piers", prefs.bridges3d) {
            AmniMapStudio.update(ctx, hostIsLight) { s -> s.copy(bridges3d = it) }
        }
        StudioSwitchRow("⛰️ Terrain Hillshade", "3D elevation shading & contours", prefs.terrain) {
            AmniMapStudio.update(ctx, hostIsLight) { s -> s.copy(terrain = it) }
        }
        StudioSwitchRow("🚦 Live Traffic", "Congestion colors & live ETA delays", prefs.traffic) {
            AmniMapStudio.update(ctx, hostIsLight) { s -> s.copy(traffic = it) }
        }
        Text("MAP STYLE (${AmniMapStudio.LOOKS.size})", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AmniMapStudio.LOOKS.forEach { (id, lbl) ->
                val sel = prefs.look == id
                Box(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (sel) p.acc else p.panel2)
                        .border(1.dp, if (sel) p.acc else p.hairline, RoundedCornerShape(9.dp))
                        .clickable { AmniMapStudio.update(ctx, hostIsLight) { it.copy(look = id) } }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(lbl, color = if (sel) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text("3D VEHICLE MODEL (${AmniMapStudio.VEHICLES.size})", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AmniMapStudio.VEHICLES.forEach { (id, lbl) ->
                val sel = prefs.vehicle == id
                Box(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (sel) p.acc else p.panel2)
                        .border(1.dp, if (sel) p.acc else p.hairline, RoundedCornerShape(9.dp))
                        .clickable { AmniMapStudio.update(ctx, hostIsLight) { it.copy(vehicle = id) } }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(lbl, color = if (sel) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text("VEHICLE PAINT", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmniMapStudio.PAINTS.forEach { (hex, name) ->
                val sel = prefs.paintHex == hex
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(hex))
                        .border(if (sel) 3.dp else 1.dp, if (sel) p.acc else p.hairline, CircleShape)
                        .clickable { AmniMapStudio.update(ctx, hostIsLight) { it.copy(paintHex = hex) } }
                )
            }
        }
        Text("ROUTING & VOICE OPTIONS", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
        StudioSwitchRow("🛣️ Avoid Highways", "Prefer local & scenic roads", prefs.avoidHighways) {
            AmniMapStudio.update(ctx, hostIsLight) { s -> s.copy(avoidHighways = it) }
        }
        StudioSwitchRow("🪙 Avoid Tolls", "Route around toll roads & bridges", prefs.avoidTolls) {
            AmniMapStudio.update(ctx, hostIsLight) { s -> s.copy(avoidTolls = it) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all" to "🔊 All Turns & Alerts", "alerts" to "⚠ Alerts Only", "mute" to "🔇 Muted").forEach { (id, lbl) ->
                val sel = prefs.voiceMode == id
                Box(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (sel) p.acc else p.panel2)
                        .border(1.dp, if (sel) p.acc else p.hairline, RoundedCornerShape(9.dp))
                        .clickable { AmniMapStudio.update(ctx, hostIsLight) { it.copy(voiceMode = id) } }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(lbl, color = if (sel) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
@Composable
private fun StudioSwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalAmni.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(p.panel2.copy(alpha = 0.72f))
            .border(1.dp, p.hairline, RoundedCornerShape(10.dp))
            .clickable { onChange(!checked) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = p.soft, fontSize = 9.5.sp)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = p.acc, checkedThumbColor = if (p.isLight) Color.White else Color(0xFF05070A)))
    }
}
@Composable
fun AmniMapStudioSheet(ctx: Context, prefs: AmniMapPrefs, hostIsLight: Boolean, onClose: () -> Unit) {
    val p = LocalAmni.current
    Box(
        Modifier
            .fillMaxSize()
            .background(p.scrim)
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        SoftSurface(
            Modifier
                .fillMaxWidth(0.87f)
                .padding(10.dp)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
            radius = 20.dp
        ) {
            Column(
                Modifier
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🗺 AMNI-MAP 3D STUDIO & OPTIONS", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(p.panel2)
                            .border(1.dp, p.hairline, RoundedCornerShape(99.dp))
                            .clickable { onClose() }
                            .padding(horizontal = 11.dp, vertical = 5.dp)
                    ) {
                        Text("DONE ✓", color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
                AmniMapStudioControls(ctx, prefs, hostIsLight, compact = true)
            }
        }
    }
}
