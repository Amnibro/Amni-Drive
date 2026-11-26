package com.amni.drive.ui
import android.app.role.RoleManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.horizontalScroll
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
    NAV("🧭", "NAVIGATION", "Voice prompts · rerouting"),
    AUTO("🔌", "AUTO-START", "Plug in & go · wireless & go"),
    LOOKS("🎨", "APPEARANCE", "Presets · accent · weather · tiles"),
    UPDATES("📦", "OTA & AMNI APPS", "Auto-updater · Amni-Map & suite"),
    CAR("🚗", "CAR / USB", "Plug phone into factory Android Auto"),
    WIRE("⚡", "AMNI WIRE", "Replace Android Auto on the wire"),
    LINK("🔗", "AMNI LINK", "Phone host · Android head unit"),
    ABOUT("ℹ️", "ABOUT", "Amni-Drive v${com.amni.drive.BuildConfig.VERSION_NAME} · AA replacement")
}
class SettingsActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TAB = "amni_settings_tab"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as AmniDriveApp
        val startTab = runCatching {
            intent.getStringExtra(EXTRA_TAB)?.let { SettingsPage.valueOf(it) }
        }.getOrNull() ?: SettingsPage.LOOKS
        setContent {
            val theme by app.themeStore.theme.collectAsStateWithLifecycle(UiTheme())
            AmniDriveTheme(rememberPalette(theme)) { SettingsScreen(app.configStore, app.themeStore, theme, startTab) { finish() } }
        }
    }
    override fun onResume() {
        super.onResume()
        com.amni.drive.update.OtaUpdater.refreshInstalled(this)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()) {
            com.amni.drive.update.OtaUpdater.resumePendingInstall(this)
        }
    }
}
@Composable
private fun SettingsScreen(store: ConfigStore, themes: ThemeStore, theme: UiTheme, initialPage: SettingsPage = SettingsPage.LOOKS, onBack: () -> Unit) {
    val p = LocalAmni.current
    var page by remember { mutableStateOf(initialPage) }
    var advanced by remember { mutableStateOf(initialPage !in setOf(SettingsPage.LOOKS, SettingsPage.UPDATES, SettingsPage.ABOUT)) }
    val ota by com.amni.drive.update.OtaUpdater.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(p.bg).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("‹", color = p.acc, fontSize = 22.sp, modifier = Modifier.clickable { onBack() }.padding(horizontal = 8.dp))
            Text("SETTINGS", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(Modifier.width(250.dp).fillMaxHeight()) {
                Column(Modifier.padding(8.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SettingsPage.entries.filter { advanced || it == SettingsPage.LOOKS || it == SettingsPage.UPDATES || it == SettingsPage.ABOUT }.forEach { pg ->
                        val sel = page == pg
                        val badge = if (pg == SettingsPage.UPDATES && ota.totalUpdatesCount > 0) " (${ota.totalUpdatesCount})" else ""
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (sel) p.accDim else Color.Transparent).border(1.dp, if (sel) p.acc.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(12.dp)).clickable { page = pg }.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(pg.icon, fontSize = 18.sp)
                            Column {
                                Text(pg.title + badge, color = if (sel || badge.isNotEmpty()) p.acc else p.ink, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Text(pg.sub, color = p.dim, fontSize = 9.sp)
                            }
                        }
                    }
                    Text(if (advanced) "HIDE ADVANCED" else "ADVANCED", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { advanced = !advanced; if (!advanced && page !in setOf(SettingsPage.LOOKS, SettingsPage.UPDATES, SettingsPage.ABOUT)) page = SettingsPage.LOOKS }.padding(horizontal = 12.dp, vertical = 14.dp))
                }
            }
            GlassCard(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (page) {
                        SettingsPage.GATE -> GatePane(store)
                        SettingsPage.NAV -> NavPane(store)
                        SettingsPage.AUTO -> AutoPane(store)
                        SettingsPage.LOOKS -> LooksPane(themes, theme)
                        SettingsPage.UPDATES -> UpdatesAndAppsPane()
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
private fun NavPane(store: ConfigStore) {
    val p = LocalAmni.current
    val scope = rememberCoroutineScope()
    val cfg by store.config.collectAsStateWithLifecycle(DriveConfig())
    SetRow("🔊", "Voice guidance", "Spoken turn-by-turn — 'In a quarter mile, turn right'. Ducks music while speaking. Also toggleable from the turn banner.", cfg.voiceNav) { scope.launch { store.setVoiceNav(it) } }
    SetRow("💬", "Message banner", "Incoming texts show sender + app on the dash with a READ button. Needs the same media/notification access as Now Playing.", cfg.msgBanner) { scope.launch { store.setMsgBanner(it) } }
    SetRow("🗣️", "Auto-read messages aloud", "Speaks new messages without a tap. OFF by default — your call, your cabin.", cfg.msgAutoRead) { scope.launch { store.setMsgAutoRead(it) } }
    SetRow("🖥️", "HU fullscreen", "Drive mode hides Android's status + nav bars — 100% Amni pixels, true head-unit feel on a mounted phone/tablet. Swipe an edge to peek the bars.", cfg.huFullscreen) { scope.launch { store.setHuFullscreen(it) } }
    Text("AUTO-REROUTE IS ALWAYS ON: DRIFT 60 M OFF THE LINE FOR 3 GPS FIXES AND A FRESH OSRM ROUTE IS PULLED (15 S COOLDOWN). SEARCH ON THE CAR GLASS: TAP ⌨ CAR KEYBOARD FOR THE HOST'S NATIVE KEYBOARD.", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 9.sp, lineHeight = 13.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(p.accDim).padding(10.dp))
}
@android.annotation.SuppressLint("MissingPermission")
@Composable
private fun AutoPane(store: ConfigStore) {
    val p = LocalAmni.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg by store.config.collectAsStateWithLifecycle(DriveConfig())
    val bonded = remember { runCatching { (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bondedDevices?.toList() ?: emptyList() }.getOrDefault(emptyList()) }
    val overlayOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(ctx)
    SetRow("🔌", "Start on power (plug in & go)", "Cable into the car → the dash opens itself. On a dedicated tablet dash this is all you need.", cfg.autoStartOnPower) { scope.launch { store.setAutoPower(it) } }
    SetRow("📶", "Start on Bluetooth (wireless & go)", "Phone links to the car stereo → the dash opens. Pick your car below so headphones don't trigger it.", cfg.autoStartOnBluetooth) { scope.launch { store.setAutoBt(it) } }
    SetRow("🚗", "Enter system car mode", "Also flips Android into CAR_DOCK / car UI on connect for bigger touch targets.", cfg.autoEnterCarMode) { scope.launch { store.setAutoCarMode(it) } }
    Text("MY CAR'S BLUETOOTH" + if (cfg.carBtMac.isNotBlank()) " · SET" else "", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    if (bonded.isEmpty()) Text("No paired Bluetooth devices found (grant Nearby devices / pair your car first).", color = p.dim, fontSize = 10.sp)
    bonded.forEach { d ->
        val mac = runCatching { d.address }.getOrDefault("")
        val name = runCatching { d.name }.getOrNull() ?: mac
        val sel = mac.equals(cfg.carBtMac, true)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (sel) p.accDim else Color.Transparent).border(1.dp, if (sel) p.acc.copy(alpha = 0.4f) else p.dim.copy(alpha = 0.2f), RoundedCornerShape(10.dp)).clickable { scope.launch { store.setCarMac(if (sel) "" else mac); store.setAutoBt(!sel) } }.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (sel) "✓" else "📻", fontSize = 14.sp)
            Column { Text(name, color = if (sel) p.acc else p.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(mac, color = p.dim, fontSize = 9.sp, fontFamily = FontFamily.Monospace) }
        }
    }
    if (!overlayOk) Text("⚠ TAP: allow 'Display over other apps' — required so the dash can open while the screen is locked/asleep.", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(p.accDim).padding(10.dp).clickable { runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } })
    Text("LIKE CARPLAY/AA: CONNECT → DASH. RUNS AS A BACKGROUND WATCH SERVICE + BOOT RESTART. THE DISTRACTION GATE STILL GOVERNS WHAT PLAYS.", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 9.sp, lineHeight = 13.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(p.accDim).padding(10.dp))
}
@Composable
private fun LooksPane(themes: ThemeStore, theme: UiTheme) {
    val p = LocalAmni.current
    val scope = rememberCoroutineScope()
    Text("HAVEN & DRIVING THEMES (${AmniPreset.entries.size})", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.5.sp)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AmniPreset.entries.forEach { preset ->
            val sel = theme.preset == preset && theme.accent == null
            val pp = paletteFor(preset, null)
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(pp.bg)
                    .border(if (sel) 2.dp else 1.dp, if (sel) p.acc else pp.line2, RoundedCornerShape(12.dp))
                    .clickable { scope.launch { themes.setPreset(preset) } }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(if (sel) "✓ Aa" else "Aa", color = pp.acc, fontFamily = FontFamily.Monospace, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(preset.label.uppercase(), color = pp.ink, fontFamily = FontFamily.Monospace, fontSize = 8.sp, letterSpacing = 0.5.sp)
            }
        }
    }
    Text("CUSTOM ACCENT", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.5.sp)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            0xFF00FF9DL, 0xFF7C5CFCL, 0xFF5BA8FFL, 0xFF00E5FFL, 0xFFFF8A3DL,
            0xFFFFD700L, 0xFFFF5A5AL, 0xFFFF2D6FL, 0xFF14FE17L, 0xFFFFFFFFL
        ).forEach { c ->
            val sel = theme.accent == c
            Box(Modifier.size(34.dp).clip(CircleShape).background(Color(c)).border(if (sel) 3.dp else 1.dp, if (sel) p.ink else Color.White.copy(alpha = 0.25f), CircleShape).clickable { scope.launch { themes.setAccent(if (sel) null else c) } })
        }
    }
    Text("ACCESSIBILITY & COMFORT", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    Text("TEXT SIZE", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.5.sp)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("NORMAL" to 1.0f, "LARGE" to 1.15f, "XL" to 1.3f).forEach { (label, sc) ->
            Seg(label, kotlin.math.abs(theme.fontScale - sc) < 0.04f) { scope.launch { themes.setFontScale(sc) } }
        }
    }
    SetRow("👁️", "High contrast mode", "Pure black/white surfaces, crisp borders, and stronger legibility for sun glare or low vision.", theme.highContrast) { scope.launch { themes.setHighContrast(it) } }
    SetRow("🧤", "Large touch targets", "Extra padding on dash buttons for bumpy roads or gloves.", theme.largeTargets) { scope.launch { themes.setLargeTargets(it) } }
    SetRow("🧘", "Reduce motion", "Disables animated weather particles and extra visual motion.", theme.reduceMotion) { scope.launch { themes.setReduceMotion(it) } }
    Text("CORNER STYLE", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.5.sp)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("SHARP" to 0, "ROUNDED" to 1, "SOFT" to 2).forEach { (label, v) -> Seg(label, theme.cornerStyle == v) { scope.launch { themes.setCornerStyle(v) } } }
    }
    SetRow("🌦️", "Weather background", "Live rain/snow particles behind the dashboard by GPS — auto-calms while MOVING.", theme.weatherBg) { scope.launch { themes.setWeather(it) } }
    Text("TILE SIZE", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.5.sp)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("COMPACT", "COZY", "BIG").forEachIndexed { i, label -> Seg(label, theme.tileScale == i) { scope.launch { themes.setScale(i) } } }
    }
    Text("TILE LAYOUT — DASH ARRANGEMENT", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.5.sp)
    Text("Tiles resize to window · Auto re-picks for phone / DHU / car glass", color = p.soft, fontSize = 11.sp)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TileLayout.entries.forEach { mode -> Seg(mode.label.uppercase(), theme.tileLayout == mode) { scope.launch { themes.setTileLayout(mode) } } }
    }
    Text("MAP STYLE", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.5.sp)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("AUTO — MATCH THEME" to -1, "DARK" to 0, "LIGHT" to 1, "SATELLITE" to 2).forEach { (label, v) -> Seg(label, theme.basemap == v) { scope.launch { themes.setBasemap(v) } } }
    }
}
@Composable
private fun WirePane() {
    val p = LocalAmni.current
    val ctx = LocalContext.current
    val app = ctx.applicationContext as AmniDriveApp
    val cfg by app.configStore.config.collectAsStateWithLifecycle(DriveConfig())
    val scope = rememberCoroutineScope()
    var certStatus by remember { mutableStateOf(com.amni.drive.wire.AaCertStore.status(ctx)) }
    var certBusy by remember { mutableStateOf(false) }
    var certMsg by remember { mutableStateOf<String?>(null) }
    val gearheadEnabled = remember {
        runCatching { ctx.packageManager.getApplicationInfo("com.google.android.projection.gearhead", 0).enabled }.getOrNull()
    }
    val overlayOk = remember { Settings.canDrawOverlays(ctx) }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        certBusy = true
        certMsg = "Importing certificate…"
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val res = runCatching {
                val files = uris.mapNotNull { u ->
                    val name = u.lastPathSegment.orEmpty().lowercase()
                    val bytes = ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } ?: return@mapNotNull null
                    name to bytes
                }
                val p12 = files.firstOrNull { (n, _) -> n.endsWith(".p12") || n.endsWith(".pfx") }
                if (p12 != null) {
                    com.amni.drive.wire.AaCertStore.importPkcs12(ctx, p12.second, "")
                        .recoverCatching { com.amni.drive.wire.AaCertStore.importPkcs12(ctx, p12.second, "android").getOrThrow() }
                        .getOrThrow()
                } else {
                    val joined = files.joinToString("\n") { (_, b) -> b.toString(Charsets.UTF_8) }
                    val certRx = Regex("-----BEGIN CERTIFICATE-----[\\s\\S]+?-----END CERTIFICATE-----")
                    val keyRx = Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]+?-----END [A-Z ]*PRIVATE KEY-----")
                    val certs = certRx.findAll(joined).map { it.value }.joinToString("\n")
                    val key = keyRx.find(joined)?.value ?: error("No PEM private key found in selected file(s)")
                    if (certs.isBlank()) error("No PEM certificate found in selected file(s)")
                    com.amni.drive.wire.AaCertStore.importPem(ctx, certs.toByteArray(Charsets.UTF_8), key.toByteArray(Charsets.UTF_8), requireGoogleRoot = false).getOrThrow()
                }
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                certBusy = false
                res.onSuccess {
                    certStatus = it
                    certMsg = "Imported & verified (valid until ${it.expiryLabel()})"
                }.onFailure {
                    certMsg = "Import failed: ${it.message}"
                }
            }
        }
    }

    Text("CAR READINESS & ZERO-ADB SETUP", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    Text(
        when (gearheadEnabled) {
            true -> "1. Android Auto is ENABLED — it may grab USB first. Tap below → Disable (or Clear defaults) — no PC or ADB needed."
            false -> "1. Android Auto is DISABLED — Amni-Drive claims USB automatically ✓"
            null -> "1. Android Auto not installed — Amni-Drive claims USB automatically ✓"
        },
        color = if (gearheadEnabled == true) p.gold else p.acc,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        lineHeight = 14.sp
    )
    if (gearheadEnabled != null) {
        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (gearheadEnabled) p.acc else p.panel2).border(1.dp, p.acc, RoundedCornerShape(12.dp)).clickable {
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:com.google.android.projection.gearhead")))
            }
        }.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                if (gearheadEnabled) "OPEN ANDROID AUTO APP INFO (DISABLE / CLEAR DEFAULTS)" else "MANAGE ANDROID AUTO APP INFO",
                color = if (gearheadEnabled) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.acc,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
    Text(
        if (overlayOk) "2. Background app launch (Overlay) GRANTED — dash can wake music & apps ✓"
        else "2. Grant 'Display over other apps' so tapping Music or Apps on the car screen wakes them while the phone is locked.",
        color = if (overlayOk) p.acc else p.soft,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        lineHeight = 14.sp
    )
    if (!overlayOk) {
        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(p.panel2).border(1.dp, p.acc, RoundedCornerShape(12.dp)).clickable {
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
            }
        }.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("ALLOW DASH APP LAUNCH (OVERLAY PERMISSION)", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
    val directEngineUp by com.amni.drive.launcher.CarApps.ready.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { com.amni.drive.launcher.CarApps.probe() }
    Text(
        if (directEngineUp) "3. Direct Car App Engine (ShellAngel) RUNNING ✓ — apps open directly inside the car display, never on your phone."
        else "3. Direct Car App Engine (ShellAngel) OFFLINE — start ShellAngel once per reboot so apps open inside the car display instead of your phone.",
        color = if (directEngineUp) p.acc else p.gold,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        lineHeight = 14.sp
    )
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(p.panel2).border(1.dp, p.acc, RoundedCornerShape(12.dp)).clickable {
        com.amni.drive.launcher.CarApps.probe()
    }.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text("RECHECK DIRECT CAR APP ENGINE", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }

    Text("CAR WEB SIGN-INS (SYNCED PHONE → CAR)", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    Text("Sign in once here using your phone keyboard & autofill — cookies are shared automatically with the car display's Media box.", color = p.soft, fontSize = 11.sp, lineHeight = 15.sp)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WebAppEntry.CATALOG.forEach { web ->
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(p.panel2).border(1.dp, p.acc.copy(alpha = 0.55f), RoundedCornerShape(10.dp)).clickable {
                WebLoginActivity.open(ctx, web.url, web.label)
            }.padding(horizontal = 11.dp, vertical = 8.dp)) {
                Text("${web.emoji} ${web.label}", color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    Text("PHONE CERTIFICATE (FACTORY HEAD UNITS)", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    val lastRefusal = com.amni.drive.wire.AaCertStore.lastAuthStatus ?: 0
    val certSummary = when (certStatus.source) {
        "DOWNLOADED", "IMPORTED" -> "${certStatus.source} · valid until ${certStatus.expiryLabel()} · Google root=${certStatus.googleRootVerified}" + if (certStatus.expired) " (EXPIRED)" else ""
        else -> "FALLBACK self-signed (factory head units usually need the AA cert)"
    } + if (lastRefusal != 0) "\nLast car auth refusal: status=$lastRefusal (auto-fallback armed)" else ""
    Text(certSummary, color = if (certStatus.googleRootVerified && !certStatus.expired && lastRefusal == 0) p.acc else p.soft, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 14.sp)
    certMsg?.let { Text(it, color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (certBusy) p.panel2 else p.acc).clickable(enabled = !certBusy) {
            certBusy = true
            certMsg = "Downloading & verifying against Google Automotive Link root…"
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val res = com.amni.drive.wire.AaCertStore.downloadFromIscle(ctx)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    certBusy = false
                    res.onSuccess {
                        certStatus = it
                        certMsg = "Verified & installed (valid until ${it.expiryLabel()})"
                    }.onFailure {
                        certMsg = "Download failed: ${it.message}"
                    }
                }
            }
        }.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(if (certBusy) "DOWNLOADING…" else "DOWNLOAD AA CERT", color = if (certBusy) p.dim else (if (p.isLight) Color.White else Color(0xFF05070A)), fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(p.panel2).border(1.dp, p.acc, RoundedCornerShape(12.dp)).clickable(enabled = !certBusy) {
            importLauncher.launch(arrayOf("*/*"))
        }.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("IMPORT PEM / P12", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        if (certStatus.source != "FALLBACK") {
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(p.panel2).border(1.dp, p.line2, RoundedCornerShape(12.dp)).clickable {
                com.amni.drive.wire.AaCertStore.clearDownloaded(ctx)
                com.amni.drive.wire.AaCertStore.clearImported(ctx)
                certStatus = com.amni.drive.wire.AaCertStore.status(ctx)
                certMsg = "Cleared — reverted to self-signed fallback"
            }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text("CLEAR", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    val waa by com.amni.drive.wire.WirelessAaService.state.collectAsStateWithLifecycle()
    var waaHostIp by remember { mutableStateOf("192.168.43.1") }
    Text("WIRELESS ANDROID AUTO (FACTORY WIRELESS & DONGLES)", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    Text(
        "Status: ${waa.phase}" +
            (if (waa.ssid.isNotBlank()) " · Wi-Fi: ${waa.ssid}" else "") +
            (if (waa.lastLog.isNotBlank()) "\n${waa.lastLog}" else ""),
        color = if (waa.running) p.acc else p.soft,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        lineHeight = 14.sp
    )
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.clip(RoundedCornerShape(10.dp))
                .background(if (waa.running) p.panel2 else p.acc)
                .border(1.dp, p.acc, RoundedCornerShape(10.dp))
                .clickable {
                    if (waa.running) com.amni.drive.wire.WirelessAaService.stop(ctx)
                    else com.amni.drive.wire.WirelessAaService.start(ctx)
                }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                if (waa.running) "STOP WIRELESS AA" else "START WIRELESS AA (BT + :5277)",
                color = if (waa.running) p.acc else (if (p.isLight) Color.White else Color(0xFF05070A)),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            Modifier.clip(RoundedCornerShape(10.dp))
                .background(p.panel2)
                .border(1.dp, p.acc.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                .clickable {
                    com.amni.drive.wire.WirelessAaService.connectTcp(ctx, waaHostIp.trim().ifEmpty { "192.168.43.1" }, 5277)
                }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text("CONNECT $waaHostIp:5277", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
    androidx.compose.foundation.text.BasicTextField(
        value = waaHostIp,
        onValueChange = { waaHostIp = it },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 11.sp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(p.panel2)
            .border(1.dp, p.line, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    )

    val audioArmed by com.amni.drive.wire.AaAudioStreamer.armed.collectAsStateWithLifecycle()
    val audioLive by com.amni.drive.wire.AaAudioStreamer.streaming.collectAsStateWithLifecycle()
    Text("CAR SPEAKER AUDIO OVER USB / WI-FI (AAP PCM)", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    Text(
        when {
            audioLive -> "STREAMING LIVE to car speakers over AAP PCM ✓"
            audioArmed -> "ARMED ✓ — plays media & navigation audio directly through the car's USB/Wi-Fi audio channel."
            else -> "Optional (needed for Honda, VW/Audi, GM, Ford & aftermarket units that mute Bluetooth when AA connects). Cars that keep Bluetooth A2DP (Kia/Hyundai) work without enabling this."
        },
        color = if (audioArmed) p.acc else p.soft,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        lineHeight = 14.sp
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.clip(RoundedCornerShape(10.dp))
                .background(if (audioArmed) p.panel2 else p.acc)
                .border(1.dp, p.acc, RoundedCornerShape(10.dp))
                .clickable {
                    if (audioArmed) com.amni.drive.wire.AaAudioStreamer.stopAll(ctx)
                    else com.amni.drive.wire.AudioCaptureGrantActivity.launch(ctx)
                }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                if (audioArmed) "DISABLE USB/WI-FI AUDIO" else "ENABLE CAR SPEAKER AUDIO (AAP)",
                color = if (audioArmed) p.acc else (if (p.isLight) Color.White else Color(0xFF05070A)),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    Text("DUAL-PATH — FULL AMNI UI ON THE CAR", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    Text("AUTO (default): uses own USB stack when Android Auto isn't projecting, or rides AA when active. OWN: always claim USB directly.", color = p.soft, fontSize = 12.sp, lineHeight = 17.sp)
    Text("MODE: ${cfg.aaPathMode.uppercase()}", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            "AUTO" to DriveConfig.PATH_AUTO,
            "RIDE (A)" to DriveConfig.PATH_RIDE,
            "OWN (B)" to DriveConfig.PATH_OWN
        ).forEach { (label, mode) ->
            val on = cfg.aaPathMode == mode
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) p.acc else p.panel2).border(1.dp, p.acc, RoundedCornerShape(10.dp)).clickable {
                scope.launch { app.configStore.setAaPathMode(mode) }
            }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(label, color = if (on && p.isLight) Color.White else if (on) Color(0xFF05070A) else p.acc, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(p.acc).clickable {
        com.amni.drive.wire.ProjectionHostService.start(ctx)
    }.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("START FULL UI PROJECTION (VIRTUAL DISPLAY)", color = if (p.isLight) Color.White else Color(0xFF05070A), fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(p.panel2).border(1.dp, p.acc, RoundedCornerShape(12.dp)).clickable {
        scope.launch { app.configStore.setAaPathMode(DriveConfig.PATH_OWN) }
        com.amni.drive.wire.AoaProbeService.claim(ctx)
    }.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("PATH B · CLAIM AOA (SETS MODE=OWN)", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(p.panel2).border(1.dp, p.acc, RoundedCornerShape(12.dp)).clickable {
        com.amni.drive.wire.AaSessionWatchService.tick(ctx)
    }.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("FORCE DUAL-PATH WATCH TICK", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
    Text("MESHNET DEV BRIDGE", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    Text("HTTP on all ifaces port ${com.amni.drive.wire.MeshDevBridgeService.PORT} (Nord Meshnet / LAN). Default RIDE never opens USB — keeps wifi adb alive. Agent uses /status /logs /action over mesh.", color = p.soft, fontSize = 12.sp, lineHeight = 17.sp)
    val meshTok = remember { com.amni.drive.wire.MeshDevBridgeService.token(ctx) }
    Text("token=$meshTok", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(p.acc).clickable {
            com.amni.drive.wire.MeshDevBridgeService.start(ctx)
        }.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("START BRIDGE", color = if (p.isLight) Color.White else Color(0xFF05070A), fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(p.panel2).border(1.dp, p.acc, RoundedCornerShape(10.dp)).clickable {
            com.amni.drive.wire.MeshDevBridgeService.stop(ctx)
        }.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("STOP BRIDGE", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
    Text("RIDE default: no openAccessory on launch/attach (fixes wifi adb death). OWN only via button above. Mesh: http://PHONE_MESH_IP:7931/status?token=…", color = p.dim, fontSize = 10.sp, lineHeight = 14.sp)
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
private fun UpdatesAndAppsPane() {
    val p = LocalAmni.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ota by com.amni.drive.update.OtaUpdater.state.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.amni.drive.update.OtaUpdater.refreshInstalled(ctx)
        if (ota.lastCheckedMs == 0L) {
            com.amni.drive.update.OtaUpdater.checkForUpdates(ctx, quiet = true)
        }
    }

    Text("OVER-THE-AIR (OTA) AUTO-UPDATER", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(p.panel2.copy(alpha = 0.85f))
            .border(1.dp, if (ota.selfUpdateAvailable) p.acc else p.line, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🚗", fontSize = 22.sp)
            Column(Modifier.weight(1f)) {
                Text(
                    "Amni-Drive v${ota.currentVersionName} (build ${ota.currentVersionCode})",
                    color = p.ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    when {
                        ota.selfUpdateAvailable && ota.selfRelease != null -> "New OTA release v${ota.selfRelease!!.versionName} (build ${ota.selfRelease!!.versionCode}) ready!"
                        ota.selfRelease != null -> "Up to date · latest on amni-scient.com is v${ota.selfRelease!!.versionName}"
                        else -> "Signed OTA feed: amni-scient.com/drive/android-latest.json"
                    },
                    color = if (ota.selfUpdateAvailable) p.acc else p.soft,
                    fontSize = 11.sp
                )
            }
        }

        ota.selfRelease?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            Text(notes, color = p.soft, fontSize = 11.sp, lineHeight = 15.sp)
        }

        if (ota.downloadingId != null) {
            val pct = (ota.downloadProgress * 100f).toInt().coerceIn(0, 100)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Downloading ${ota.downloadingId}… $pct%",
                    color = p.acc,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(p.bg)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(ota.downloadProgress.coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(99.dp))
                            .background(p.acc)
                    )
                }
            }
        }

        ota.statusMsg?.let { msg ->
            Text(msg, color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 14.sp)
        }
        ota.errorMsg?.let { err ->
            Text("⚠ $err", color = p.err, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 14.sp)
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ota.selfUpdateAvailable && ota.selfRelease != null) {
                val rel = ota.selfRelease!!
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (ota.downloadingId == null) p.acc else p.panel)
                        .clickable(enabled = ota.downloadingId == null) {
                            scope.launch {
                                com.amni.drive.update.OtaUpdater.downloadAndInstall(
                                    ctx, "amni-drive", "Amni-Drive v${rel.versionName}", rel.url, rel.sha256
                                )
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        "⬇ INSTALL OTA v${rel.versionName}",
                        color = if (p.isLight) Color.White else Color(0xFF05070A),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (ota.checking) p.panel else p.accDim)
                    .border(1.dp, p.acc.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .clickable(enabled = !ota.checking && ota.downloadingId == null) {
                        scope.launch { com.amni.drive.update.OtaUpdater.checkForUpdates(ctx, quiet = false) }
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    if (ota.checking) "CHECKING…" else "CHECK FOR UPDATES",
                    color = p.acc,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (ota.pendingInstallApkPath != null) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(p.acc)
                        .clickable { com.amni.drive.update.OtaUpdater.resumePendingInstall(ctx) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        "FINISH INSTALL →",
                        color = if (p.isLight) Color.White else Color(0xFF05070A),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(p.panel)
                    .border(1.dp, p.hairline, RoundedCornerShape(10.dp))
                    .clickable {
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(com.amni.drive.update.OtaUpdater.SITE_URL)))
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text("AMNI-SCIENT.COM ↗", color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    SetRow(
        "🔄",
        "Auto-check OTA updates",
        "Quietly checks amni-scient.com every 6 hours for signed Amni-Drive & Amni-Map updates. SHA-256 verified before install.",
        ota.autoCheckEnabled
    ) { com.amni.drive.update.OtaUpdater.setAutoCheckEnabled(ctx, it) }

    Spacer(Modifier.height(4.dp))
    Text("PACKAGED AMNI APPS FOR YOUR CAR", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
    Text(
        "Install and keep the companion Amni-Scient apps updated OTA right from here. Amni-Map runs natively inside Amni-Drive's live car map tile.",
        color = p.soft,
        fontSize = 11.sp,
        lineHeight = 15.sp
    )

    val list = ota.apps.ifEmpty {
        com.amni.drive.update.OtaUpdater.FALLBACK_CATALOG.map {
            com.amni.drive.update.OtaUpdater.CompanionAppStatus(it, false, 0L, null)
        }
    }

    list.forEach { st ->
        val app = st.app
        val busy = ota.downloadingId == app.id
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (app.carDashNative) p.accDim.copy(alpha = 0.65f) else p.panel2.copy(alpha = 0.72f))
                .border(
                    1.dp,
                    if (st.updateAvailable || app.carDashNative) p.acc.copy(alpha = 0.65f) else p.line,
                    RoundedCornerShape(12.dp)
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(app.emoji, fontSize = 22.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(app.name, color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (app.carDashNative) {
                        Text(
                            "CAR DASH NATIVE",
                            color = p.acc,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(p.bg.copy(alpha = 0.75f))
                                .border(1.dp, p.acc.copy(alpha = 0.6f), RoundedCornerShape(99.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(app.blurb, color = p.soft, fontSize = 10.sp, lineHeight = 13.sp)
                val verLine = when {
                    st.updateAvailable -> "Installed v${st.installedVersionName ?: st.installedVersionCode} → OTA v${app.versionName} available"
                    st.installed -> "Installed v${st.installedVersionName ?: st.installedVersionCode} ✓"
                    app.versionName.isNotBlank() -> "Available: v${app.versionName}" + if (app.size > 0L) " · ${app.size / 1_048_576} MB" else ""
                    else -> "Direct signed APK from amni-scient.com"
                }
                Text(
                    verLine,
                    color = if (st.updateAvailable) p.acc else p.dim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = if (st.updateAvailable) FontWeight.Bold else FontWeight.Normal
                )
            }
            val btnLabel = when {
                busy -> "${(ota.downloadProgress * 100f).toInt()}%"
                st.updateAvailable -> "UPDATE"
                st.installed -> "OPEN"
                else -> "INSTALL"
            }
            val isPrimary = !st.installed || st.updateAvailable
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isPrimary) p.acc else p.panel)
                    .border(1.dp, if (isPrimary) p.acc else p.hairline, RoundedCornerShape(10.dp))
                    .clickable(enabled = ota.downloadingId == null) {
                        if (st.installed && !st.updateAvailable) {
                            runCatching {
                                ctx.packageManager.getLaunchIntentForPackage(app.packageName)?.let { launch ->
                                    ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }
                        } else {
                            scope.launch {
                                com.amni.drive.update.OtaUpdater.downloadAndInstall(
                                    ctx, app.id, "${app.name} ${app.versionName}", app.downloadUrl, app.sha256
                                )
                            }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(
                    btnLabel,
                    color = if (isPrimary) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
@Composable
private fun AboutPane() {
    val p = LocalAmni.current
    val ctx = LocalContext.current
    Text("AMNI//DRV", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
    Text(
        "v${com.amni.drive.BuildConfig.VERSION_NAME} (build ${com.amni.drive.BuildConfig.VERSION_CODE}) — Universal USB Android Auto replacement with native Amni-Map embed, customizable resizable cockpit layouts, built-in OTA auto-updater, and packaged Amni Apps hub.",
        color = p.soft,
        fontSize = 12.sp,
        lineHeight = 17.sp
    )
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
    Text("MAP DATA © OPENSTREETMAP CONTRIBUTORS · TILES CARTO + ESRI · ROUTING OSRM · WEATHER OPEN-METEO · AMNI-SCIENT.COM", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 8.sp, lineHeight = 12.sp)
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
