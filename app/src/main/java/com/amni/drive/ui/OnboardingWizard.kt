package com.amni.drive.ui
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amni.drive.config.ConfigStore
import com.amni.drive.launcher.CarEmbed
import com.amni.drive.media.MediaBrowserHub
import com.amni.drive.media.MediaSessionBridge
import com.amni.drive.update.OtaUpdater
import com.amni.drive.wire.AaAudioStreamer
import com.amni.drive.wire.AaCertStore
import com.amni.drive.wire.AoaProbeService
import com.amni.drive.wire.AudioCaptureGrantActivity
import com.amni.drive.wire.WirelessAaService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
@Composable
fun OnboardingWizard(
    theme: UiTheme,
    themeStore: ThemeStore,
    configStore: ConfigStore,
    onFinish: () -> Unit
) {
    val p = LocalAmni.current
    val ctx = LocalContext.current
    val cfg = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var step by remember { mutableIntStateOf(0) }
    var refreshTick by remember { mutableIntStateOf(0) }
    val ota by OtaUpdater.state.collectAsStateWithLifecycle()
    val audioArmed by AaAudioStreamer.armed.collectAsStateWithLifecycle()
    val waa by WirelessAaService.state.collectAsStateWithLifecycle()
    var certStatus by remember { mutableStateOf(AaCertStore.status(ctx)) }
    var certBusy by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_RESUME) {
                refreshTick++
                OtaUpdater.refreshInstalled(ctx)
                MediaSessionBridge.ensureStarted(ctx)
                certStatus = AaCertStore.status(ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Unit) {
        val sp = ctx.getSharedPreferences("amni_onboard_cal", 0)
        if (!sp.getBoolean("calibrated_v13", false)) {
            sp.edit().putBoolean("calibrated_v13", true).apply()
            val isPortraitPhone = cfg.screenHeightDp > cfg.screenWidthDp * 1.15f
            if (isPortraitPhone) {
                themeStore.setDockPosition(1)
                if (abs(theme.fontScale - 1.0f) < 0.02f) themeStore.setFontScale(1.08f)
            }
        }
        OtaUpdater.refreshInstalled(ctx)
        if (ota.lastCheckedMs == 0L) OtaUpdater.checkForUpdates(ctx, quiet = true)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshTick++
    }
    val locOk = remember(refreshTick) {
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    val micOk = remember(refreshTick) {
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    val overlayOk = remember(refreshTick) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(ctx)
    }
    val notifMediaOk = remember(refreshTick) { MediaSessionBridge.hasAccess(ctx) }
    val amniMapInstalled = remember(refreshTick, ota.apps) { CarEmbed.supports(ctx, CarEmbed.MAP_PKG) }
    val gearheadEnabled = remember(refreshTick) {
        runCatching { ctx.packageManager.getApplicationInfo("com.google.android.projection.gearhead", 0).enabled }.getOrNull()
    }
    val detectedAudio = remember(refreshTick) {
        MediaBrowserHub.discoverInstalledAudioPackages(ctx).mapNotNull { pkg ->
            runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() to pkg }.getOrNull()
        }
    }
    val stepTitles = listOf(
        "1 · DISPLAY, THEME & UI SCALE",
        "2 · INSTALL AMNI-MAP 3D",
        "3 · PERMISSIONS & USB CLAIM",
        "4 · SIGN-IN CARRY-OVER & CAR AUDIO",
        "5 · PLUG IN OR CONNECT WIRELESS"
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .padding(14.dp)
    ) {
        GlassCard(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(p.acc))
                    Text("AMNI-DRIVE SETUP GUIDE", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.weight(1f))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(p.panel2)
                            .border(1.dp, p.hairline, RoundedCornerShape(99.dp))
                            .clickable {
                                scope.launch { configStore.setOnboardingCompleted(true) }
                                onFinish()
                            }
                            .padding(horizontal = 11.dp, vertical = 5.dp)
                    ) {
                        Text("SKIP →", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    stepTitles.indices.forEach { idx ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (idx <= step) p.acc else p.panel2)
                                .clickable { step = idx }
                        )
                    }
                }
                Text(stepTitles[step], color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    when (step) {
                        0 -> {
                            Text("Calibrate text, tile sizing, and theme for both your phone and your car's head unit. Light themes automatically switch both the cockpit and map into daylight mode.", color = p.soft, fontSize = 12.5.sp, lineHeight = 18.sp)
                            Text("THEME MODE (SYNCED TO MAP)", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                listOf(AmniPreset.AMNI_DARK, AmniPreset.AMNI_LIGHT, AmniPreset.HAVEN, AmniPreset.BRAID_LIGHT, AmniPreset.OLED, AmniPreset.HIGH_CONTRAST).forEach { pr ->
                                    val sel = theme.preset == pr
                                    WizardChip(pr.label, sel) {
                                        scope.launch {
                                            themeStore.setPreset(pr)
                                            AmniMapStudio.syncHostTheme(ctx, pr.light)
                                        }
                                    }
                                }
                            }
                            Text("CAR DISPLAY UI SCALE", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                listOf("85%" to 0.85f, "100%" to 1.0f, "115%" to 1.15f, "130%" to 1.30f).forEach { (lbl, sc) ->
                                    WizardChip(lbl, abs(theme.carUiScale - sc) < 0.04f) { scope.launch { themeStore.setCarUiScale(sc) } }
                                }
                            }
                            Text("TEXT READABILITY", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                listOf("Standard" to 1.0f, "Comfortable" to 1.12f, "Large" to 1.22f, "XL" to 1.32f).forEach { (lbl, fs) ->
                                    WizardChip(lbl, abs(theme.fontScale - fs) < 0.04f) { scope.launch { themeStore.setFontScale(fs) } }
                                }
                            }
                            Text("NAVIGATION BAR POSITION", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                WizardChip("Bottom Center Dock", theme.dockPosition == 1) { scope.launch { themeStore.setDockPosition(1) } }
                                WizardChip("Left Driver Rail", theme.dockPosition == 0) { scope.launch { themeStore.setDockPosition(0) } }
                            }
                            Text("BESPOKE COCKPIT GEOMETRY", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                listOf(
                                    TileLayout.AUTO to "✦ Auto",
                                    TileLayout.TWIN_CHRONO to "◯ Twin Chrono",
                                    TileLayout.SOLAR_ORBIT to "◎ Solar Orbit",
                                    TileLayout.HEX_COCKPIT to "⬡ Aero Hex",
                                    TileLayout.CAPSULE_DECK to "⬭ Capsule Deck",
                                    TileLayout.BALANCED to "⊞ Tri-Cockpit"
                                ).forEach { (ly, lbl) ->
                                    WizardChip(lbl, theme.tileLayout == ly) { scope.launch { themeStore.setTileLayout(ly) } }
                                }
                            }
                        }
                        1 -> {
                            val mapSt = ota.apps.firstOrNull { it.app.packageName == CarEmbed.MAP_PKG }
                            val mapCompanion = mapSt?.app ?: OtaUpdater.FALLBACK_CATALOG.firstOrNull { it.packageName == CarEmbed.MAP_PKG }
                            val bundled = ota.bundledMap != null
                            val mapNeedsUpdate = mapSt?.updateAvailable == true
                            Text(
                                if (bundled) "Amni-Map 3D is prebuilt directly inside this Amni-Drive APK — tap below to install or update it instantly with zero internet download!"
                                else "Amni-Map embeds natively inside Amni-Drive's map tile with 3D buildings, 3D trees, raised highway bridges, 3D vehicles, live traffic, and perspective top-dead-center orientation.",
                                color = p.soft,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp
                            )
                            WizardActionCard(
                                title = when {
                                    amniMapInstalled && !mapNeedsUpdate -> "✓ Amni-Map Installed & Up to Date"
                                    amniMapInstalled && mapNeedsUpdate -> "⚡ Update Amni-Map to v${mapCompanion?.versionName ?: "Latest"}"
                                    bundled -> "⚡ Install Built-In Amni-Map v${ota.bundledMap?.versionName ?: "2.14.0"}"
                                    else -> "Install Amni-Map Companion"
                                },
                                subtitle = when {
                                    amniMapInstalled && !mapNeedsUpdate -> "3D buildings, trees, bridges & vehicle studio ready on car dash."
                                    bundled -> "Included inside Amni-Drive APK · instant offline install (no download needed)."
                                    else -> "Downloads signed APK directly from amni-scient.com and installs in 1 tap."
                                },
                                ok = amniMapInstalled && !mapNeedsUpdate,
                                buttonLabel = when {
                                    ota.downloadingId == "amni-map" -> "${(ota.downloadProgress * 100f).toInt()}%"
                                    amniMapInstalled && !mapNeedsUpdate -> "INSTALLED ✓"
                                    amniMapInstalled && mapNeedsUpdate -> "⚡ UPDATE"
                                    bundled -> "⚡ INSTALL BUILT-IN"
                                    else -> "⬇ INSTALL AMNI-MAP"
                                }
                            ) {
                                if ((!amniMapInstalled || mapNeedsUpdate) && mapCompanion != null && ota.downloadingId == null) {
                                    scope.launch {
                                        OtaUpdater.downloadAndInstall(ctx, mapCompanion.id, mapCompanion.name, mapCompanion.downloadUrl, mapCompanion.sha256)
                                    }
                                }
                            }
                            ota.statusMsg?.let { Text(it, color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp) }
                            ota.errorMsg?.let { Text("⚠ $it", color = p.err, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp) }
                        }
                        2 -> {
                            Text("Grant these once on your phone so GPS, voice search, background music wake, and USB handoff work reliably every drive.", color = p.soft, fontSize = 12.5.sp, lineHeight = 18.sp)
                            WizardActionCard(
                                title = "📍 Location (Live Map & Heading)",
                                subtitle = "Shows dynamic free-drive location and top-dead-center heading even without an active route.",
                                ok = locOk,
                                buttonLabel = if (locOk) "GRANTED ✓" else "GRANT"
                            ) {
                                val perms = buildList {
                                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                                }.toTypedArray()
                                permLauncher.launch(perms)
                            }
                            WizardActionCard(
                                title = "🎤 Microphone (Car Voice Search)",
                                subtitle = "Powers 🎤 voice destination & song search right from the car touchscreen.",
                                ok = micOk,
                                buttonLabel = if (micOk) "GRANTED ✓" else "GRANT"
                            ) {
                                permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                            }
                            WizardActionCard(
                                title = "🎵 Media & Notification Access",
                                subtitle = "Carries over your signed-in Spotify / music sessions, album art & incoming message banners.",
                                ok = notifMediaOk,
                                buttonLabel = if (notifMediaOk) "ENABLED ✓" else "ENABLE"
                            ) {
                                MediaSessionBridge.requestAccess(ctx)
                            }
                            WizardActionCard(
                                title = "🪟 Display Over Other Apps",
                                subtitle = "Lets Amni-Drive wake up automatically when you plug in with your phone locked.",
                                ok = overlayOk,
                                buttonLabel = if (overlayOk) "ALLOWED ✓" else "ALLOW"
                            ) {
                                runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))) }
                            }
                            if (gearheadEnabled != null) {
                                WizardActionCard(
                                    title = "🔌 Claim USB from Stock Android Auto",
                                    subtitle = if (gearheadEnabled) "Stock Android Auto is enabled and may grab USB before Amni-Drive. Tap to Disable or Clear Defaults." else "Stock Android Auto is disabled — Amni-Drive owns USB automatically!",
                                    ok = !gearheadEnabled,
                                    buttonLabel = if (!gearheadEnabled) "CLAIMED ✓" else "OPEN AA INFO"
                                ) {
                                    runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:com.google.android.projection.gearhead"))) }
                                }
                            }
                        }
                        3 -> {
                            Text("Music apps you're already signed into on your phone carry over automatically into the Media Box — no re-typing Spotify passwords on the car screen.", color = p.soft, fontSize = 12.5.sp, lineHeight = 18.sp)
                            if (detectedAudio.isNotEmpty()) {
                                Text("DETECTED SIGNED-IN PHONE AUDIO APPS (${detectedAudio.size})", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    detectedAudio.forEach { (label, pkg) ->
                                        WizardChip("✓ $label", true) { MediaBrowserHub.wakeAndPlay(ctx, pkg) }
                                    }
                                }
                            }
                            WizardActionCard(
                                title = "🔊 Audio via Amni-Drive (USB / Wi-Fi Car Speakers)",
                                subtitle = "Essential for Honda, VW/Audi, Ford & GM stereos that mute Bluetooth when USB connects. Streams 48 kHz PCM/AAC audio directly over Amni-Drive.",
                                ok = audioArmed,
                                buttonLabel = if (audioArmed) "ARMED ✓" else "ENABLE AUDIO"
                            ) {
                                if (audioArmed) AaAudioStreamer.stopAll(ctx) else AudioCaptureGrantActivity.launch(ctx)
                            }
                            Text("OPTIONAL WEB VIDEO SIGN-INS (SHARED WITH CAR)", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                WebAppEntry.CATALOG.take(7).forEach { web ->
                                    WizardChip("${web.emoji} ${web.label}", false) { WebLoginActivity.open(ctx, web.url, web.label) }
                                }
                            }
                        }
                        else -> {
                            Text("Choose how you connect to your vehicle. If the app ever crashes on plug-in, Amni-Drive automatically captures a privacy-scrubbed diagnostic log for anthony@amni-scient.com.", color = p.soft, fontSize = 12.5.sp, lineHeight = 18.sp)
                            val certOk = certStatus.googleRootVerified && !certStatus.expired
                            WizardActionCard(
                                title = "🔐 Factory Head-Unit TLS Certificate",
                                subtitle = if (certOk) "Verified Google Automotive root certificate ready (valid until ${certStatus.expiryLabel()})." else "Required once so Honda, Toyota, Ford, VW & GM factory radios trust the USB/Wi-Fi handshake.",
                                ok = certOk,
                                buttonLabel = if (certBusy) "LOADING…" else if (certOk) "READY ✓" else "DOWNLOAD CERT"
                            ) {
                                if (!certBusy) {
                                    certBusy = true
                                    scope.launch(Dispatchers.IO) {
                                        val r = AaCertStore.downloadFromIscle(ctx)
                                        withContext(Dispatchers.Main) {
                                            certBusy = false
                                            r.onSuccess { certStatus = it }
                                        }
                                    }
                                }
                            }
                            WizardActionCard(
                                title = "🔌 USB Cable Connection",
                                subtitle = "1. Plug a data-capable USB cable into your car's Android Auto USB port.\n2. Tap 'Always allow' on the USB prompt — Amni-Drive projects onto your dash.",
                                ok = true,
                                buttonLabel = "CLAIM USB NOW"
                            ) {
                                AoaProbeService.claim(ctx)
                            }
                            WizardActionCard(
                                title = "📶 Wireless Android Auto",
                                subtitle = "Pair Bluetooth with your car stereo or wireless AA adapter, then start Wireless AA.",
                                ok = waa.running,
                                buttonLabel = if (waa.running) "RUNNING ✓" else "START WIRELESS"
                            ) {
                                if (waa.running) WirelessAaService.stop(ctx) else WirelessAaService.start(ctx)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (step > 0) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(p.panel2)
                                .border(1.dp, p.hairline, RoundedCornerShape(12.dp))
                                .clickable { step-- }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text("◀ BACK", color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(p.acc)
                            .clickable {
                                if (step < stepTitles.lastIndex) {
                                    step++
                                } else {
                                    scope.launch { configStore.setOnboardingCompleted(true) }
                                    onFinish()
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text(
                            if (step < stepTitles.lastIndex) "NEXT STEP →" else "LAUNCH AMNI-DRIVE ✓",
                            color = if (p.isLight) Color.White else Color(0xFF05070A),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun WizardChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalAmni.current
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) p.acc else p.panel2)
            .border(1.dp, if (selected) p.acc else p.hairline, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
@Composable
private fun WizardActionCard(title: String, subtitle: String, ok: Boolean, buttonLabel: String, onClick: () -> Unit) {
    val p = LocalAmni.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(if (ok) p.accDim.copy(alpha = 0.55f) else p.panel2.copy(alpha = 0.85f))
            .border(1.dp, if (ok) p.acc.copy(alpha = 0.65f) else p.hairline, RoundedCornerShape(13.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = p.soft, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (ok) p.panel else p.acc)
                .border(1.dp, p.acc, RoundedCornerShape(10.dp))
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text(
                buttonLabel,
                color = if (ok) p.acc else (if (p.isLight) Color.White else Color(0xFF05070A)),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
