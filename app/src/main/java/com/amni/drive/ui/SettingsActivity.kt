package com.amni.drive.ui
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amni.drive.AmniDriveApp
import com.amni.drive.LinkActivity
import com.amni.drive.config.ConfigStore
import com.amni.drive.config.DriveConfig
import com.amni.drive.link.LinkProto
import kotlinx.coroutines.launch
private enum class SettingsPage(val icon: String, val title: String, val sub: String) {
    GATE("🛡️", "DISTRACTION GATE", "Per-display · no hard locks"),
    LOOKS("🎨", "APPEARANCE", "Presets · accent · weather · tiles"),
    CAR("🚗", "CAR / USB", "Plug phone into factory Android Auto"),
    WIRE("⚡", "AMNI WIRE", "Replace Android Auto on the wire"),
    LINK("🔗", "AMNI LINK", "Phone host · Android head unit"),
    ABOUT("ℹ️", "ABOUT", "Amni-Drive v1.3.0 · AA replacement")
}
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as AmniDriveApp
        setContent {
            val theme by app.themeStore.theme.collectAsStateWithLifecycle(UiTheme())
            AmniDriveTheme(rememberPalette(theme)) { SettingsScreen(app.configStore, app.themeStore, theme) { finish() } }
        }
    }
}
@Composable
private fun SettingsScreen(store: ConfigStore, themes: ThemeStore, theme: UiTheme, onBack: () -> Unit) {
    val p = LocalAmni.current
    var page by remember { mutableStateOf(SettingsPage.GATE) }
    Column(Modifier.fillMaxSize().background(p.bg).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("‹", color = p.acc, fontSize = 22.sp, modifier = Modifier.clickable { onBack() }.padding(horizontal = 8.dp))
            Text("SETTINGS", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(Modifier.width(250.dp).fillMaxHeight()) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SettingsPage.entries.forEach { pg ->
                        val sel = page == pg
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (sel) p.accDim else Color.Transparent).border(1.dp, if (sel) p.acc.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(12.dp)).clickable { page = pg }.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(pg.icon, fontSize = 18.sp)
                            Column {
                                Text(pg.title, color = if (sel) p.acc else p.ink, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Text(pg.sub, color = p.dim, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
            GlassCard(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (page) {
                        SettingsPage.GATE -> GatePane(store)
                        SettingsPage.LOOKS -> LooksPane(themes, theme)
                        SettingsPage.CAR -> CarUsbPane()
                        SettingsPage.WIRE -> WirePane()
                        SettingsPage.LINK -> LinkPane()
                        SettingsPage.ABOUT -> AboutPane()
                    }
                }
            }
        }
    }
}
@Composable
private fun GatePane(store: ConfigStore) {
    val p = LocalAmni.current
    val scope = rememberCoroutineScope()
    val cfg by store.config.collectAsStateWithLifecycle(DriveConfig())
    SetRow("🛡️", "Driver display gate", "Light gate while MOVING — video & games pause up front. One tap to bypass, always.", cfg.driverGateEnabled) { scope.launch { store.setDriver(it) } }
    SetRow("📺", "Rear display gate", "UNGATED by default — the backseat is a free country.", cfg.rearGateEnabled) { scope.launch { store.setRear(it) } }
    SetRow("↪️", "Auto-route to rear", "A gated launch up front jumps straight to the rear screen instead of dead-ending.", cfg.autoRouteRearDisplay) { scope.launch { store.setAutoRoute(it) } }
    SetRow("⚡", "Master gate", "Kills every gate for this drive — re-arms next ignition.", cfg.masterGateEnabled) { scope.launch { store.setMaster(it) } }
    Text("NAV · MEDIA · PHONE ARE NEVER GATED. EVERY DECISION FLOWS THROUGH GATEMANAGER.DECIDE() — WHAT OUI SEE IS WHAT RUNS.", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 9.sp, lineHeight = 13.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(p.accDim).padding(10.dp))
}
@Composable
private fun LooksPane(themes: ThemeStore, theme: UiTheme) {
    val p = LocalAmni.current
    val scope = rememberCoroutineScope()
    Text("PRESET THEMES — FROM THE AMNI-SCIENT FAMILY", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.5.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AmniPreset.entries.forEach { preset ->
            val sel = theme.preset == preset && theme.accent == null
            val pp = paletteFor(preset, null)
            Column(Modifier.clip(RoundedCornerShape(12.dp)).background(pp.bg).border(1.dp, if (sel) p.acc else p.line2, RoundedCornerShape(12.dp)).clickable { scope.launch { themes.setPreset(preset) } }.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Aa", color = pp.acc, fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(preset.label.uppercase(), color = pp.acc, fontFamily = FontFamily.Monospace, fontSize = 7.sp, letterSpacing = 0.5.sp)
            }
        }
    }
    Text("CUSTOM ACCENT", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.5.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(0xFF00FF9DL, 0xFF7FCFFFL, 0xFFFF8A3DL, 0xFFB18CFFL, 0xFFFF5A5AL, 0xFFFFD700L).forEach { c ->
            val sel = theme.accent == c
            Box(Modifier.size(34.dp).clip(CircleShape).background(Color(c)).border(if (sel) 3.dp else 1.dp, if (sel) p.ink else Color.White.copy(alpha = 0.2f), CircleShape).clickable { scope.launch { themes.setAccent(if (sel) null else c) } })
        }
    }
    SetRow("🌦️", "Weather background", "Live rain/snow particles behind the dashboard by GPS — auto-calms while MOVING.", theme.weatherBg) { scope.launch { themes.setWeather(it) } }
    Text("TILE SIZE", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.5.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("COMPACT", "COZY", "BIG").forEachIndexed { i, label -> Seg(label, theme.tileScale == i) { scope.launch { themes.setScale(i) } } }
    }
    Text("MAP STYLE", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.5.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("AUTO — MATCH THEME" to -1, "DARK" to 0, "LIGHT" to 1, "SATELLITE" to 2).forEach { (label, v) -> Seg(label, theme.basemap == v) { scope.launch { themes.setBasemap(v) } } }
    }
}
@Composable
private fun WirePane() {
    val p = LocalAmni.current
    Text("AMNI WIRE — REPLACE ANDROID AUTO", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    Text("Goal: phone runs Amni as the AA host. Factory head units get our H.264 UI + touch over the same class of wire Google uses (AOA/USB or wireless), grounded in the public reverse-engineered stack (aasdk/OpenAuto lineage).", color = p.soft, fontSize = 12.sp, lineHeight = 17.sp)
    Text("SHIPPED", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.5.sp)
    Text("• wire/ frame+session+host · multi-channel Link · H264 scaffold · AOA probe service\n• Open source: Apache-2.0 · ROADMAP · live car captures in docs/research/", color = p.soft, fontSize = 11.sp, lineHeight = 16.sp)
    val ctx = LocalContext.current
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(p.acc).clickable {
        com.amni.drive.wire.AoaProbeService.start(ctx)
        com.amni.drive.wire.AoaProbeService.poll(ctx)
    }.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("START AOA PROBE (FOREGROUND)", color = if (p.isLight) Color.White else Color(0xFF05070A), fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
    Text("NEXT ON THE CAR", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.5.sp)
    Text("1. Plug USB while ADB wireless debug stays up.\n2. We capture AOA probes / session attempts in logcat.\n3. Align protobuf payloads with aasdk message set.\n4. First IDR accepted by factory HU = milestone.\nUntil then: CAR uses Car App Library soft path; LINK uses full Amni UI on Android receivers.", color = p.soft, fontSize = 11.sp, lineHeight = 16.sp)
    Text("Personal testing project. Built on published reverse-engineering for interop — not a Play-certified Google host.", color = p.dim, fontSize = 10.sp, lineHeight = 14.sp)
}
@Composable
private fun CarUsbPane() {
    val p = LocalAmni.current
    val ctx = LocalContext.current
    Text("ANDROID AUTO — CAR & HEAD UNIT SERVER", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    Text("Factory cars often hide sideloaded Car App Library apps (even with Unknown sources). Use the phone's Head unit server + Desktop Head Unit on the PC to prove Amni works, then ship via Play for the real dash.", color = p.soft, fontSize = 12.sp, lineHeight = 17.sp)
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(p.acc).clickable {
        runCatching { ctx.startActivity(Intent("com.google.android.projection.gearhead.SETTINGS")) }
            .onFailure {
                runCatching {
                    ctx.startActivity(Intent().setClassName("com.google.android.projection.gearhead", "com.google.android.projection.gearhead.companion.settings.DefaultSettingsActivity"))
                }
            }
    }.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("OPEN ANDROID AUTO SETTINGS", color = if (p.isLight) Color.White else Color(0xFF05070A), fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
    Text("PATH A — HEAD UNIT SERVER (PC FAKE DASH)", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.5.sp)
    Text("1. Open Android Auto on the phone → ⋮ menu → Start head unit server (notification should appear).\n2. Developer settings → Unknown sources ON · Application mode = Developer.\n3. On the PC (this machine already has DHU): run Amni-Drive\\scripts\\run_dhu.ps1\n4. Accept prompts on the phone. In the DHU window open Apps → Amni Drive.", color = p.soft, fontSize = 11.sp, lineHeight = 16.sp)
    Text("PATH B — REAL CAR", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.5.sp)
    Text("1. Unknown sources ON · Customize launcher → enable Amni Drive if listed.\n2. Unplug 10s → plug in → Apps grid.\n3. If still missing: Google is filtering non-Play car apps on your unit — next step is Play Internal Testing.", color = p.soft, fontSize = 11.sp, lineHeight = 16.sp)
    Text("WHY HEAD UNIT SERVER HELPS", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.5.sp)
    Text("It's Google's official developer path. The car radio is a closed host; the head unit server + Desktop Head Unit is a host that accepts developer apps.", color = p.soft, fontSize = 11.sp, lineHeight = 16.sp)
}
@Composable
private fun LinkPane() {
    val p = LocalAmni.current
    val ctx = LocalContext.current
    Text("AMNI LINK — ANDROID HEAD UNITS ONLY", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    Text("Full custom UI stream to an Android head unit / tablet on the same Wi-Fi. Multi-channel v2: control :${LinkProto.PORT} + video :${LinkProto.VIDEO_PORT}. Stock factory AA radios need AMNI WIRE (in progress) or CAR soft path.", color = p.soft, fontSize = 12.sp, lineHeight = 17.sp)
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(p.acc).clickable {
        ctx.startActivity(Intent(ctx, LinkActivity::class.java))
    }.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("OPEN AMNI LINK", color = if (p.isLight) Color.White else Color(0xFF05070A), fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
    Text("SETUP", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.5.sp)
    Text("1. Phone + head unit on the same network (phone hotspot is perfect).\n2. Phone → Amni Link → PHONE HOST (leave it running).\n3. Head unit → install same APK → Amni Link → HEAD-UNIT RECEIVER → tap the host (or type phone IP).\n4. Drive UI fills the dash; touch works.", color = p.soft, fontSize = 11.sp, lineHeight = 16.sp)
    Text("You can also keep the classic path: install Amni-Drive as the head unit's default launcher for full native apps with no streaming.", color = p.dim, fontSize = 10.sp, lineHeight = 14.sp)
}
@Composable
private fun AboutPane() {
    val p = LocalAmni.current
    val ctx = LocalContext.current
    Text("AMNI//DRV", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
    Text("v1.3.0 — Amni Wire scaffold to replace Android Auto on the wire (open reverse-engineered stack). Multi-channel Link, modern shell, Car App fallback.", color = p.soft, fontSize = 12.sp, lineHeight = 17.sp)
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(p.acc).clickable {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rm = ctx.getSystemService(Context.ROLE_SERVICE) as RoleManager
                if (rm.isRoleAvailable(RoleManager.ROLE_HOME)) ctx.startActivity(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                else ctx.startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
            } else ctx.startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
        }
    }.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("SET AS DEFAULT LAUNCHER (HEAD UNIT)", color = if (p.isLight) Color.White else Color(0xFF05070A), fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
    Text("ON THE HEAD UNIT: TAP ABOVE (OR PRESS HOME) → PICK AMNI-DRIVE → ALWAYS. THE HOME BUTTON THEN OPENS AMNI//DRV INSTEAD OF THE STOCK LAUNCHER. ON A PHONE, JUST OPEN THE APP NORMALLY — DON'T SET IT AS YOUR PHONE LAUNCHER.", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 8.sp, lineHeight = 12.sp)
    Text("MAP DATA © OPENSTREETMAP CONTRIBUTORS · TILES CARTO + ESRI · ROUTING OSRM · WEATHER OPEN-METEO", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 8.sp, lineHeight = 12.sp)
}
@Composable
private fun SetRow(icon: String, title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalAmni.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.panel2.copy(alpha = 0.7f)).border(1.dp, p.line, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(icon, fontSize = 20.sp)
        Column(Modifier.weight(1f)) {
            Text(title, color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = p.dim, fontSize = 10.sp, lineHeight = 13.sp)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = p.acc, checkedThumbColor = if (p.isLight) Color.White else Color(0xFF05070A)))
    }
}
@Composable
private fun Seg(label: String, sel: Boolean, onClick: () -> Unit) {
    val p = LocalAmni.current
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (sel) p.acc else p.panel2).border(1.dp, if (sel) p.acc else p.line2, RoundedCornerShape(10.dp)).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(label, color = if (sel) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.soft, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}
