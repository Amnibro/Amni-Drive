package com.amni.drive.ui
import android.content.Context
import android.content.Intent
import android.location.Location
import android.media.AudioManager
import android.net.ConnectivityManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amni.drive.LinkActivity
import com.amni.drive.aa.AaNavState
import com.amni.drive.config.ConfigStore
import com.amni.drive.config.DriveConfig
import com.amni.drive.gate.DriveState
import com.amni.drive.gate.GateDecision
import com.amni.drive.launcher.AppEntry
import com.amni.drive.map.OsrmRouting
import com.amni.drive.map.Place
import com.amni.drive.map.ReportStore
import com.amni.drive.map.RoutePoint
import com.amni.drive.map.RouteResult
import com.amni.drive.map.RouteStep
import com.amni.drive.map.TileStore
import com.amni.drive.map.TurnGuidance
import com.amni.drive.map.VoiceGuide
import com.amni.drive.map.rememberMapCamera
import com.amni.drive.media.MediaSessionBridge
import com.amni.drive.weather.WeatherNow
import com.amni.drive.weather.Wx
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
enum class DrivePage { HOME, APPS, NAV }
@Composable
fun Dashboard(
apps: List<AppEntry>,
cfg: DriveConfig,
driveState: DriveState,
passenger: Boolean,
theme: UiTheme,
location: Location?,
weather: WeatherNow?,
tiles: TileStore,
reportStore: ReportStore,
rearAvailable: Boolean,
onLaunch: (AppEntry) -> GateDecision,
onLaunchRear: (AppEntry) -> Unit,
onToggleGate: () -> Unit,
onToggleWeather: (Boolean) -> Unit,
onConfirmPassenger: () -> Unit,
onOpenSettings: () -> Unit,
initialPage: DrivePage = DrivePage.HOME,
carSurface: Boolean = false,
surfaceDisplayId: Int? = null,
onSetTileLayout: (TileLayout) -> Unit = {},
onCycleTheme: () -> Unit = {}
) {
val p = rememberPalette(theme)
val ctxRoot = LocalContext.current
CompositionLocalProvider(LocalAmni provides p) {
var page by remember { mutableStateOf(initialPage) }
LaunchedEffect(initialPage) { page = initialPage }
var menuOpen by remember { mutableStateOf(false) }
var layoutPicker by remember { mutableStateOf(false) }
var blocked by remember { mutableStateOf<AppEntry?>(null) }
var mediaHint by remember { mutableStateOf<String?>(null) }
var mediaPicker by remember { mutableStateOf(false) }
val mediaAppsList = remember(apps) { apps.filter { it.category == com.amni.drive.gate.AppCategory.MEDIA || it.packageName.contains("spotify", true) || it.packageName.contains("music", true) || it.packageName.contains("youtube.music", true) } }
val pickMedia: (AppEntry) -> Unit = { app ->
if (carSurface) {
playViaBrowser(ctxRoot, app.packageName) { mediaKey(ctxRoot, KeyEvent.KEYCODE_MEDIA_PLAY) }
mediaHint = app.label
} else {
val d = onLaunch(app)
if (!d.allow) blocked = app
else {
mediaKey(ctxRoot, KeyEvent.KEYCODE_MEDIA_PLAY)
mediaHint = app.label
}
}
}
var route by remember { mutableStateOf<RouteResult?>(null) }
val stops = remember { mutableStateListOf<RoutePoint>() }
var routing by remember { mutableStateOf(false) }
var showLayers by remember { mutableStateOf(false) }
var showReport by remember { mutableStateOf(false) }
val camera = rememberMapCamera()
val reports by reportStore.reports.collectAsState()
LaunchedEffect(theme.basemap, p.isLight) { camera.basemap = if (theme.basemap < 0) (if (p.isLight) 1 else 0) else theme.basemap }
val moving = driveState == DriveState.MOVING || driveState == DriveState.UNKNOWN
val speedMph = ((location?.speed ?: 0f) * 2.23694f).roundToInt()
val addPoint: (Double, Double) -> Unit = { la, lo -> if (stops.isEmpty()) stops.add(RoutePoint(la, lo)) else stops.add(stops.size - 1, RoutePoint(la, lo)) }
val onGo: (Place) -> Unit = { stops.clear(); stops.add(RoutePoint(it.lat, it.lon)); AaNavState.destination = it }
val onAddStop: (Place) -> Unit = { if (stops.isEmpty()) stops.add(RoutePoint(it.lat, it.lon)) else stops.add(stops.size - 1, RoutePoint(it.lat, it.lon)) }
val onEnd: () -> Unit = { stops.clear(); route = null; AaNavState.destination = null; AaNavState.route = null }
val stopsKey = stops.joinToString("|") { "${it.lat},${it.lon}" }
LaunchedEffect(stopsKey, location == null) {
if (stops.isEmpty()) { route = null; return@LaunchedEffect }
val from = location ?: return@LaunchedEffect
routing = true
route = OsrmRouting.route(listOf(RoutePoint(from.latitude, from.longitude)) + stops.toList())
AaNavState.route = route
routing = false
}
val voice = remember { VoiceGuide(ctxRoot) }
DisposableEffect(Unit) { onDispose { voice.shutdown() } }
var stepIdx by remember { mutableStateOf(0) }
LaunchedEffect(route) { stepIdx = 0; voice.reset() }
val cue = remember(route, location, stepIdx) { route?.let { r -> location?.let { l -> TurnGuidance.cue(r, l.latitude, l.longitude, stepIdx) } } }
LaunchedEffect(cue) { cue?.let { c -> if (c.idx != stepIdx) stepIdx = c.idx; voice.maybeSpeak(c, cfg.voiceNav) } }
val navScope = rememberCoroutineScope()
val onToggleVoice: () -> Unit = { navScope.launch { ConfigStore(ctxRoot).setVoiceNav(!cfg.voiceNav) } }
val pendingGo by AaNavState.pendingGo.collectAsState()
LaunchedEffect(pendingGo) { pendingGo?.let { g -> page = DrivePage.NAV; onGo(g); AaNavState.consumeGo() } }
var offCount by remember { mutableStateOf(0) }
var lastRerouteAt by remember { mutableStateOf(0L) }
LaunchedEffect(location) {
val r = route ?: return@LaunchedEffect
val l = location ?: return@LaunchedEffect
if (stops.isEmpty() || routing) return@LaunchedEffect
offCount = if (TurnGuidance.distToRouteM(r.points, l.latitude, l.longitude) > TurnGuidance.OFF_ROUTE_M) offCount + 1 else 0
val now = System.currentTimeMillis()
if (offCount >= TurnGuidance.OFF_ROUTE_SAMPLES && now - lastRerouteAt > TurnGuidance.REROUTE_COOLDOWN_MS) {
lastRerouteAt = now
offCount = 0
voice.say("Rerouting", cfg.voiceNav)
routing = true
route = OsrmRouting.route(listOf(RoutePoint(l.latitude, l.longitude)) + stops.toList())
AaNavState.route = route
routing = false
}
}
val ctx = LocalContext.current
val netOk by produceState(true) {
val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
while (true) { value = cm.activeNetwork != null; delay(3000) }
}
BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(p.bg, if (p.isLight) Color(0xFFE8ECF0) else Color(0xFF05070A))))) {
val aspect = maxWidth / maxHeight
val micro = maxHeight < 380.dp || maxWidth < 600.dp
val dense = carSurface || maxHeight < 560.dp || aspect > 1.5f || maxWidth < 960.dp
val railW = when {
micro -> 48.dp
maxWidth < 720.dp -> 56.dp
dense -> 64.dp
else -> 92.dp
}
val pad = when { micro -> 3.dp; dense -> 5.dp; else -> 12.dp }
val gap = when { micro -> 3.dp; dense -> 5.dp; else -> 10.dp }
val contentW = maxWidth - railW
val widgetW = when {
contentW < 640.dp -> 120.dp
contentW < 900.dp -> 148.dp
dense -> 168.dp
else -> 240.dp
}
val showTopBar = !carSurface && maxHeight > 480.dp && !micro
val mediaPulse by AaNavState.mediaPulse.collectAsState()
var mediaFlash by remember { mutableStateOf(false) }
LaunchedEffect(mediaPulse) { if (mediaPulse > 0L) { mediaFlash = true; page = DrivePage.HOME; delay(1200); mediaFlash = false } }
val layout = resolveTileLayout(theme.tileLayout, micro, dense, contentW, maxHeight, carSurface)
if (theme.weatherBg && !micro && !carSurface) WeatherLayer(weather?.wx ?: Wx.NONE, moving, Modifier.fillMaxSize())
Row(Modifier.fillMaxSize()) {
Rail(page, cfg.driverGateEnabled && cfg.masterGateEnabled, { menuOpen = true }, { page = it }, onToggleGate, railW, dense || micro)
Column(Modifier.weight(1f).fillMaxHeight()) {
if (showTopBar) TopBar(driveState, speedMph, weather, passenger)
else if ((carSurface || dense) && !micro) CarStatusStrip(driveState, speedMph, weather, passenger, if (carSurface) 84.dp else 0.dp)
Box(Modifier.weight(1f).fillMaxWidth()) {
AnimatedContent(
targetState = page,
transitionSpec = {
if (carSurface) fadeIn(tween(90)) togetherWith fadeOut(tween(90))
else (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + slideInHorizontally { it / 24 }) togetherWith
(fadeOut() + slideOutHorizontally { -it / 24 })
},
modifier = Modifier.fillMaxSize(),
label = "page"
) { pg ->
when (pg) {
DrivePage.APPS -> AppDrawer(apps, cfg, driveState, passenger, appTileScale(theme.tileScale, contentW, dense)) { app ->
val mediaApp = app.category == com.amni.drive.gate.AppCategory.MEDIA || app.packageName.contains("spotify", true) || app.packageName.contains("music", true)
if (carSurface && mediaApp) {
playViaBrowser(ctxRoot, app.packageName) { mediaKey(ctxRoot, KeyEvent.KEYCODE_MEDIA_PLAY) }
mediaHint = app.label
page = DrivePage.HOME
AaNavState.openMedia()
} else {
val d = onLaunch(app)
if (!d.allow) blocked = app
else if (carSurface && surfaceDisplayId != null) mediaHint = "On dash · ${app.label}"
}
}
DrivePage.NAV -> Box(Modifier.fillMaxSize().padding(pad)) {
MapCard(Modifier.fillMaxSize(), camera, tiles, route, reports, stops, location, routing, true, netOk, showLayers, showReport, { showLayers = it }, { showReport = it }, reportStore, addPoint, onGo, onAddStop, onEnd, dense || micro, if (carSurface) ({ AaNavState.openHostSearch() }) else null)
}
DrivePage.HOME -> HomeTiles(
layout = layout,
pad = pad,
gap = gap,
widgetW = widgetW,
dense = dense || micro,
carTopPad = if (carSurface) 34.dp else 0.dp,
mediaFlash = mediaFlash,
mediaHint = mediaHint,
surfaceDisplayId = surfaceDisplayId,
camera = camera,
tiles = tiles,
route = route,
reports = reports,
stops = stops,
location = location,
routing = routing,
netOk = netOk,
showLayers = showLayers,
showReport = showReport,
onShowLayers = { showLayers = it },
onShowReport = { showReport = it },
reportStore = reportStore,
addPoint = addPoint,
onGo = onGo,
onAddStop = onAddStop,
onEnd = onEnd,
weather = weather,
speedMph = speedMph,
cfg = cfg,
mediaApps = mediaAppsList,
onOpenMediaPicker = { mediaPicker = true },
hostKeyboard = if (carSurface) ({ AaNavState.openHostSearch() }) else null
)
}
}
cue?.let { c -> if (page != DrivePage.APPS) TurnBanner(c, route?.steps?.getOrNull(c.idx + 1), cfg.voiceNav, onToggleVoice, dense || micro, Modifier.align(Alignment.TopCenter).padding(top = if (carSurface) 6.dp else 8.dp)) }
}
}
}
if (menuOpen) MenuDrawer(page, rearAvailable, cfg.driverGateEnabled && cfg.masterGateEnabled, theme.weatherBg, carSurface, theme.tileLayout,
onPage = { page = it; menuOpen = false }, onToggleGate = onToggleGate, onToggleWeather = onToggleWeather,
onSettings = { menuOpen = false; onOpenSettings() }, onClose = { menuOpen = false }, onMedia = { menuOpen = false; page = DrivePage.HOME; AaNavState.openMedia() },
onTileLayout = { menuOpen = false; layoutPicker = true }, themeLabel = theme.preset.label, onTheme = onCycleTheme)
if (layoutPicker) TileLayoutPicker(theme.tileLayout, layout, { onSetTileLayout(it); layoutPicker = false }, { layoutPicker = false }, carSurface)
if (mediaPicker) MediaSourceSheet(mediaAppsList, { mediaPicker = false }, { pickMedia(it); mediaPicker = false })
blocked?.let { app ->
GateSheet(app, rearAvailable,
onRear = { onLaunchRear(app); blocked = null },
onPassenger = { onConfirmPassenger(); if (onLaunch(app).allow) blocked = null },
onDismiss = { blocked = null })
}
}
}
}
private fun resolveTileLayout(pref: TileLayout, micro: Boolean, dense: Boolean, contentW: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp, carSurface: Boolean): TileLayout {
if (pref != TileLayout.AUTO) return if (micro && pref != TileLayout.MAP_ONLY) TileLayout.MAP_ONLY else pref
return when {
micro || contentW < 560.dp -> TileLayout.MAP_ONLY
h < 420.dp || contentW < 720.dp -> TileLayout.MAP_FOCUS
carSurface && dense -> TileLayout.BALANCED
contentW >= 1100.dp -> TileLayout.BALANCED
else -> TileLayout.MEDIA_FOCUS
}
}
private fun appTileScale(user: Int, contentW: androidx.compose.ui.unit.Dp, dense: Boolean): Int = when {
contentW < 560.dp -> 0
dense -> minOf(user, 1)
else -> user
}
@Composable
private fun HomeTiles(
layout: TileLayout,
pad: androidx.compose.ui.unit.Dp,
gap: androidx.compose.ui.unit.Dp,
widgetW: androidx.compose.ui.unit.Dp,
dense: Boolean,
carTopPad: androidx.compose.ui.unit.Dp = 0.dp,
mediaFlash: Boolean,
mediaHint: String?,
surfaceDisplayId: Int?,
camera: com.amni.drive.map.MapCamera,
tiles: TileStore,
route: RouteResult?,
reports: List<com.amni.drive.map.Report>,
stops: List<RoutePoint>,
location: Location?,
routing: Boolean,
netOk: Boolean,
showLayers: Boolean,
showReport: Boolean,
onShowLayers: (Boolean) -> Unit,
onShowReport: (Boolean) -> Unit,
reportStore: ReportStore,
addPoint: (Double, Double) -> Unit,
onGo: (Place) -> Unit,
onAddStop: (Place) -> Unit,
onEnd: () -> Unit,
weather: WeatherNow?,
speedMph: Int,
cfg: DriveConfig,
mediaApps: List<AppEntry> = emptyList(),
onOpenMediaPicker: () -> Unit = {},
hostKeyboard: (() -> Unit)? = null
) {
val p = LocalAmni.current
val map: @Composable (Modifier) -> Unit = { mod ->
MapCard(mod, camera, tiles, route, reports, stops, location, routing, false, netOk, showLayers, showReport, onShowLayers, onShowReport, reportStore, addPoint, onGo, onAddStop, onEnd, dense, hostKeyboard)
}
val mediaMod = Modifier.then(if (mediaFlash) Modifier.border(2.dp, p.acc, RoundedCornerShape(18.dp)) else Modifier)
val widgets: @Composable (Modifier) -> Unit = { mod ->
Column(mod, verticalArrangement = Arrangement.spacedBy(gap)) {
WeatherCard(weather, dense)
TripCard(speedMph, route, dense)
GateHintCard(cfg, dense)
}
}
val media: @Composable (Modifier, Boolean) -> Unit = { mod, d ->
MediaCard(mod.then(mediaMod), d, mediaHint, surfaceDisplayId, mediaApps, onOpenMediaPicker)
}
when (layout) {
TileLayout.MAP_ONLY -> Box(Modifier.fillMaxSize().padding(pad)) {
map(Modifier.fillMaxSize())
Row(
Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(6.dp).clip(RoundedCornerShape(14.dp))
.background(p.panel.copy(alpha = 0.92f)).border(1.dp, p.hairline, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
horizontalArrangement = Arrangement.SpaceEvenly,
verticalAlignment = Alignment.CenterVertically
) {
Text(weather?.let { "${it.emoji} ${it.tempF}°" } ?: "WX --", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
Text("$speedMph MPH", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)
MediaMiniBar(Modifier.weight(1f).padding(horizontal = 4.dp))
Text(if (cfg.masterGateEnabled && cfg.driverGateEnabled) "GATE" else "OPEN", color = if (cfg.masterGateEnabled && cfg.driverGateEnabled) p.acc else p.err, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}
}
TileLayout.STACKED -> Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.spacedBy(gap)) {
map(Modifier.weight(1.55f).fillMaxWidth())
Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
media(Modifier.weight(1.1f).fillMaxHeight(), dense)
widgets(Modifier.weight(0.9f).fillMaxHeight())
}
}
TileLayout.MAP_FOCUS -> Row(Modifier.fillMaxSize().padding(pad), horizontalArrangement = Arrangement.spacedBy(gap)) {
map(Modifier.weight(2.2f).fillMaxHeight())
Column(Modifier.width(widgetW).fillMaxHeight().padding(top = carTopPad), verticalArrangement = Arrangement.spacedBy(gap)) {
WeatherCard(weather, dense)
TripCard(speedMph, route, dense)
GateHintCard(cfg, dense)
media(Modifier.weight(1f).fillMaxWidth(), true)
}
}
TileLayout.MEDIA_FOCUS -> Row(Modifier.fillMaxSize().padding(pad), horizontalArrangement = Arrangement.spacedBy(gap)) {
map(Modifier.weight(1.25f).fillMaxHeight())
media(Modifier.weight(1.15f).fillMaxHeight().then(if (widgetW < 130.dp) Modifier.padding(top = carTopPad) else Modifier), dense)
if (widgetW >= 130.dp) widgets(Modifier.width(widgetW).fillMaxHeight().padding(top = carTopPad))
}
TileLayout.BALANCED, TileLayout.AUTO -> Row(Modifier.fillMaxSize().padding(pad), horizontalArrangement = Arrangement.spacedBy(gap)) {
map(Modifier.weight(if (dense) 1.65f else 1.5f).fillMaxHeight())
media(Modifier.weight(if (dense) 0.95f else 1f).fillMaxHeight(), dense)
widgets(Modifier.width(widgetW).fillMaxHeight().padding(top = carTopPad))
}
}
}
@Composable
private fun TileLayoutPicker(selected: TileLayout, effective: TileLayout, onPick: (TileLayout) -> Unit, onClose: () -> Unit, compact: Boolean = false) {
val p = LocalAmni.current
val rowH = if (compact) 38.dp else 52.dp
Box(Modifier.fillMaxSize().background(p.scrim).clickable { onClose() }, contentAlignment = Alignment.Center) {
SoftSurface(Modifier.fillMaxWidth(if (compact) 0.6f else 0.72f).padding(if (compact) 6.dp else 12.dp), radius = 22.dp) {
Column(Modifier.padding(if (compact) 10.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
Row(verticalAlignment = Alignment.CenterVertically) {
Text("TILE LAYOUT", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = if (compact) 11.sp else 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
Spacer(Modifier.weight(1f))
Text("✕", color = p.dim, modifier = Modifier.clickable { onClose() })
}
if (!compact) Text("Active: ${effective.label}" + if (selected == TileLayout.AUTO && effective != TileLayout.AUTO) " (auto→${effective.label})" else "", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
TileLayout.entries.forEach { mode ->
val sel = selected == mode
Row(
Modifier.fillMaxWidth().height(rowH).clip(RoundedCornerShape(if (compact) 10.dp else 14.dp))
.background(if (sel) p.accDim else Color.Transparent)
.border(1.dp, if (sel) p.acc.copy(alpha = 0.4f) else p.hairline, RoundedCornerShape(if (compact) 10.dp else 14.dp))
.clickable { onPick(mode) }.padding(horizontal = if (compact) 8.dp else 12.dp),
verticalAlignment = Alignment.CenterVertically,
horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
) {
LayoutThumb(mode, sel)
Column(Modifier.weight(1f)) {
Text(mode.label.uppercase(), color = if (sel) p.acc else p.ink, fontFamily = FontFamily.Monospace, fontSize = if (compact) 10.sp else 12.sp, fontWeight = FontWeight.Bold)
if (!compact) Text(mode.blurb, color = p.dim, fontSize = 11.sp, maxLines = 1)
}
if (sel) Text("●", color = p.acc, fontSize = 12.sp)
}
}
if (!compact) Text("Tiles resize to window width/height · Auto re-picks as glass changes", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
}
}
}
}
@Composable
private fun LayoutThumb(mode: TileLayout, sel: Boolean) {
val p = LocalAmni.current
val a = if (sel) p.acc else p.dim
Box(Modifier.size(44.dp, 30.dp).clip(RoundedCornerShape(6.dp)).background(p.panel2).border(1.dp, a.copy(alpha = 0.35f), RoundedCornerShape(6.dp)).padding(3.dp)) {
when (mode) {
TileLayout.MAP_ONLY -> Box(Modifier.fillMaxSize().background(a.copy(alpha = 0.45f), RoundedCornerShape(3.dp)))
TileLayout.STACKED -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
Box(Modifier.weight(1.4f).fillMaxWidth().background(a.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
Box(Modifier.weight(1f).fillMaxHeight().background(a.copy(alpha = 0.3f), RoundedCornerShape(2.dp)))
Box(Modifier.weight(1f).fillMaxHeight().background(a.copy(alpha = 0.25f), RoundedCornerShape(2.dp)))
}
}
TileLayout.MAP_FOCUS -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
Box(Modifier.weight(2.2f).fillMaxHeight().background(a.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
Box(Modifier.weight(0.7f).fillMaxHeight().background(a.copy(alpha = 0.28f), RoundedCornerShape(2.dp)))
}
TileLayout.MEDIA_FOCUS -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
Box(Modifier.weight(1.1f).fillMaxHeight().background(a.copy(alpha = 0.45f), RoundedCornerShape(2.dp)))
Box(Modifier.weight(1.2f).fillMaxHeight().background(a.copy(alpha = 0.35f), RoundedCornerShape(2.dp)))
Box(Modifier.weight(0.7f).fillMaxHeight().background(a.copy(alpha = 0.25f), RoundedCornerShape(2.dp)))
}
TileLayout.BALANCED, TileLayout.AUTO -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
Box(Modifier.weight(1.5f).fillMaxHeight().background(a.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
Box(Modifier.weight(1f).fillMaxHeight().background(a.copy(alpha = 0.32f), RoundedCornerShape(2.dp)))
Box(Modifier.weight(0.75f).fillMaxHeight().background(a.copy(alpha = 0.25f), RoundedCornerShape(2.dp)))
}
}
}
}
@Composable
private fun Rail(page: DrivePage, gateOn: Boolean, onMenu: () -> Unit, onPage: (DrivePage) -> Unit, onToggleGate: () -> Unit, railW: androidx.compose.ui.unit.Dp = 92.dp, compact: Boolean = false) {
val p = LocalAmni.current
Column(
Modifier.width(railW).fillMaxHeight()
.background(Brush.verticalGradient(listOf(p.panel.copy(alpha = 0.95f), p.bg.copy(alpha = 0.9f))))
.border(width = 1.dp, brush = Brush.horizontalGradient(listOf(Color.Transparent, p.hairline)), shape = RoundedCornerShape(0.dp))
.padding(vertical = if (compact) 6.dp else 14.dp, horizontal = if (compact) 2.dp else 8.dp),
horizontalAlignment = Alignment.CenterHorizontally,
verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)
) {
if (!compact) {
Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 8.dp)) {
Box(Modifier.size(10.dp).clip(CircleShape).background(p.acc))
Spacer(Modifier.height(6.dp))
Text("AMNI", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
Text("DRIVE", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 3.sp)
}
} else {
Box(Modifier.size(if (railW < 58.dp) 6.dp else 8.dp).clip(CircleShape).background(p.acc))
}
RailBtn(Icons.Rounded.Menu, "MENU", false, compact, railW, onClick = onMenu)
RailBtn(Icons.Rounded.Home, "HOME", page == DrivePage.HOME, compact, railW) { onPage(DrivePage.HOME) }
RailBtn(Icons.Rounded.Apps, "APPS", page == DrivePage.APPS, compact, railW) { onPage(DrivePage.APPS) }
RailBtn(Icons.Rounded.Map, "NAV", page == DrivePage.NAV, compact, railW) { onPage(DrivePage.NAV) }
Spacer(Modifier.weight(1f))
RailBtn(Icons.Rounded.Security, if (gateOn) "GATE" else "OPEN", gateOn, compact, railW, err = !gateOn, onClick = onToggleGate)
}
}
@Composable
private fun RailBtn(icon: ImageVector, label: String, sel: Boolean, compact: Boolean = false, railW: androidx.compose.ui.unit.Dp = 92.dp, err: Boolean = false, onClick: () -> Unit) {
val p = LocalAmni.current
val tint = if (err) p.err else if (sel) p.acc else p.dim
val scale by animateFloatAsState(if (sel) 1.04f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "rail")
val micro = compact && railW < 58.dp
val boxW = if (micro) 44.dp else if (compact) 52.dp else 76.dp
val boxH = if (micro) 44.dp else if (compact) 50.dp else 70.dp
val iconSz = if (micro) 20.dp else if (compact) 22.dp else 26.dp
Column(
Modifier.scale(scale).size(boxW, boxH).clip(RoundedCornerShape(if (compact) 12.dp else 18.dp))
.background(if (sel || err) Brush.verticalGradient(listOf(tint.copy(alpha = 0.18f), tint.copy(alpha = 0.06f))) else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)))
.border(1.dp, if (sel || err) tint.copy(alpha = 0.45f) else Color.Transparent, RoundedCornerShape(if (compact) 12.dp else 18.dp))
.clickable { onClick() },
horizontalAlignment = Alignment.CenterHorizontally,
verticalArrangement = Arrangement.Center
) {
Icon(icon, label, Modifier.size(iconSz), tint = tint)
if (!compact) {
Spacer(Modifier.height(4.dp))
Text(label, color = tint, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, letterSpacing = 0.8.sp)
}
}
}
@Composable
private fun CarStatusStrip(state: DriveState, speedMph: Int, weather: WeatherNow?, passenger: Boolean, endPad: androidx.compose.ui.unit.Dp = 0.dp) {
val p = LocalAmni.current
val clock by produceState(LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm"))) {
while (true) { value = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm")); delay(10000) }
}
val moving = state == DriveState.MOVING || state == DriveState.UNKNOWN
val stTxt = when (state) {
DriveState.PARKED -> "PARKED"
DriveState.IDLING -> "STOP"
DriveState.MOVING -> "$speedMph MPH"
DriveState.UNKNOWN -> "GATED"
}
Row(
Modifier.fillMaxWidth().height(36.dp).padding(start = 8.dp, end = 8.dp + endPad),
verticalAlignment = Alignment.CenterVertically,
horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
Text("AMNI", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
Chip((if (passenger) "PSGR · " else "") + stTxt, if (moving) p.gold else p.acc)
weather?.let { Chip("${it.emoji} ${it.tempF}°", p.soft) }
Spacer(Modifier.weight(1f))
Text(clock, color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold)
}
}
@Composable
private fun MenuDrawer(page: DrivePage, rearAvailable: Boolean, gateOn: Boolean, weatherOn: Boolean, carSurface: Boolean, tileLayout: TileLayout, onPage: (DrivePage) -> Unit, onToggleGate: () -> Unit, onToggleWeather: (Boolean) -> Unit, onSettings: () -> Unit, onClose: () -> Unit, onMedia: () -> Unit, onTileLayout: () -> Unit, themeLabel: String = "", onTheme: () -> Unit = {}) {
val p = LocalAmni.current
val ctx = LocalContext.current
Row(Modifier.fillMaxSize().background(p.scrim)) {
SoftSurface(Modifier.width(320.dp).fillMaxHeight().padding(10.dp), radius = 24.dp) {
Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
Box(Modifier.size(8.dp).clip(CircleShape).background(p.acc))
Text("AMNI DRIVE", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
Spacer(Modifier.weight(1f))
Box(Modifier.size(36.dp).clip(CircleShape).background(p.panel2).clickable { onClose() }, contentAlignment = Alignment.Center) {
Text("✕", color = p.soft, fontSize = 14.sp)
}
}
Spacer(Modifier.height(8.dp))
if (!carSurface) {
MenuRow(Icons.Rounded.Home, "Home", "Dashboard & map", page == DrivePage.HOME) { onPage(DrivePage.HOME) }
MenuRow(Icons.Rounded.Apps, "All Apps", "Everything installed", page == DrivePage.APPS) { onPage(DrivePage.APPS) }
MenuRow(Icons.Rounded.Map, "Navigation", "Full-screen map", page == DrivePage.NAV) { onPage(DrivePage.NAV) }
}
MenuRow(Icons.Rounded.MusicNote, "Media", if (carSurface) "Controls beside map" else "In-dash media panel", false) { onMedia() }
MenuRow(Icons.Rounded.Palette, "Theme", "$themeLabel · tap to cycle", false) { onTheme() }
MenuRow(Icons.Rounded.ViewModule, "Tile layout", tileLayout.label + " · fit glass", false) { onTileLayout() }
if (!carSurface) {
MenuRow(Icons.Rounded.Phone, "Phone", "Dialer", false) {
runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
onClose()
}
MenuRow(Icons.Rounded.Tv, "Rear Screen", if (rearAvailable) "Detected — auto-routes gated apps" else "No rear display detected", false) {}
MenuRow(Icons.Rounded.Cast, "Amni Link", "Phone host · head-unit receiver", false) {
ctx.startActivity(Intent(ctx, LinkActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); onClose()
}
}
Box(Modifier.fillMaxWidth().height(1.dp).background(p.hairline).padding(vertical = 6.dp))
MenuSwitch(Icons.Rounded.Security, "Driver gate", gateOn) { onToggleGate() }
MenuSwitch(Icons.Rounded.WaterDrop, "Weather background", weatherOn) { onToggleWeather(it) }
if (!carSurface) {
Box(Modifier.fillMaxWidth().height(1.dp).background(p.hairline))
MenuRow(Icons.Rounded.Settings, "Settings", "Gate · themes · car · link", false, onSettings)
Spacer(Modifier.weight(1f))
Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
Icon(Icons.Rounded.Info, null, Modifier.size(14.dp), tint = p.dim)
Text("v1.2.0 · modern shell · AA · link", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 8.sp)
}
}
}
}
Box(Modifier.weight(1f).fillMaxHeight().clickable { onClose() })
}
}
@Composable
private fun MenuRow(icon: ImageVector, label: String, sub: String, sel: Boolean, onClick: () -> Unit) {
val p = LocalAmni.current
Row(
Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp))
.background(if (sel) p.accDim else Color.Transparent)
.border(1.dp, if (sel) p.acc.copy(alpha = 0.35f) else Color.Transparent, RoundedCornerShape(16.dp))
.clickable { onClick() }.padding(horizontal = 12.dp),
verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
) {
Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (sel) p.acc.copy(alpha = 0.15f) else p.panel2), contentAlignment = Alignment.Center) {
Icon(icon, label, Modifier.size(22.dp), tint = if (sel) p.acc else p.soft)
}
Column {
Text(label, color = if (sel) p.acc else p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
Text(sub, color = p.dim, fontSize = 11.sp, maxLines = 1)
}
}
}
@Composable
private fun MenuSwitch(icon: ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
val p = LocalAmni.current
Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
Icon(icon, label, Modifier.size(22.dp), tint = if (checked) p.acc else p.dim)
Text(label, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = p.acc, checkedThumbColor = if (p.isLight) Color.White else Color(0xFF05070A)))
}
}
@Composable
private fun TopBar(state: DriveState, speedMph: Int, weather: WeatherNow?, passenger: Boolean) {
val p = LocalAmni.current
val hour = LocalTime.now().hour
val greet = when { hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; else -> "Good evening" }
val clock by produceState(LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm"))) {
while (true) { value = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm")); delay(10000) }
}
Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
Column {
Text(greet.uppercase(), color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.5.sp)
Text("Anthony", color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
}
Spacer(Modifier.weight(1f))
val moving = state == DriveState.MOVING || state == DriveState.UNKNOWN
val stTint = if (moving) p.gold else p.acc
val stTxt = when (state) {
DriveState.PARKED -> "PARKED"
DriveState.IDLING -> "STOPPED"
DriveState.MOVING -> "MOVING · $speedMph MPH"
DriveState.UNKNOWN -> "NO SIGNAL · GATED"
}
Chip((if (passenger) "PSGR · " else "") + stTxt, stTint)
weather?.let { Chip("${it.emoji} ${it.tempF}°", p.soft) }
SoftSurface(Modifier.height(40.dp), radius = 12.dp, elevated = false) {
Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
Text(clock, color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 18.sp, fontWeight = FontWeight.Bold)
}
}
}
}
@Composable
fun Chip(text: String, tint: Color) {
Row(
Modifier.clip(RoundedCornerShape(99.dp))
.background(Brush.horizontalGradient(listOf(tint.copy(alpha = 0.14f), tint.copy(alpha = 0.06f))))
.border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(99.dp))
.padding(horizontal = 12.dp, vertical = 7.dp),
verticalAlignment = Alignment.CenterVertically,
horizontalArrangement = Arrangement.spacedBy(6.dp)
) {
Box(Modifier.size(7.dp).clip(CircleShape).background(tint))
Text(text, color = tint, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
}
}
private fun playViaBrowser(ctx: Context, pkg: String, fallback: () -> Unit) {
val svc = runCatching { ctx.packageManager.queryIntentServices(Intent("android.media.browse.MediaBrowserService").setPackage(pkg), 0).firstOrNull()?.serviceInfo }.getOrNull() ?: return fallback()
var b: android.support.v4.media.MediaBrowserCompat? = null
val browser = android.support.v4.media.MediaBrowserCompat(ctx, android.content.ComponentName(svc.packageName, svc.name), object : android.support.v4.media.MediaBrowserCompat.ConnectionCallback() {
override fun onConnected() {
runCatching { android.support.v4.media.session.MediaControllerCompat(ctx, b!!.sessionToken).transportControls.play() }.onFailure { fallback() }
android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ runCatching { b?.disconnect() } }, 4000)
}
override fun onConnectionFailed() { fallback() }
}, null)
b = browser
runCatching { browser.connect() }.onFailure { fallback() }
}
private fun mediaKey(ctx: Context, code: Int) {
val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
val t = SystemClock.uptimeMillis()
am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
}
@Composable
private fun rememberMediaSource(ctx: Context): Pair<String, Boolean> {
var label by remember { mutableStateOf("No active music") }
var playing by remember { mutableStateOf(false) }
DisposableEffect(ctx) {
val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
val cb = object : AudioManager.AudioPlaybackCallback() {
override fun onPlaybackConfigChanged(configs: MutableList<android.media.AudioPlaybackConfiguration>?) {
val active = configs.orEmpty().mapNotNull { c ->
runCatching {
val m = c.javaClass.methods.firstOrNull { it.name == "getClientPackageName" && it.parameterCount == 0 }
(m?.invoke(c) as? String)?.takeIf { it.isNotBlank() }
}.getOrNull()
}.distinct()
playing = am.isMusicActive
label = when {
active.any { it.contains("spotify", true) } -> "Spotify"
active.isNotEmpty() -> active.first().substringAfterLast('.').replaceFirstChar { it.uppercase() }
am.isMusicActive -> "Music playing"
else -> "No active music"
}
}
}
am.registerAudioPlaybackCallback(cb, null)
cb.onPlaybackConfigChanged(am.activePlaybackConfigurations)
onDispose { am.unregisterAudioPlaybackCallback(cb) }
}
LaunchedEffect(Unit) {
val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
while (true) {
playing = am.isMusicActive
if (!playing && label == "Music playing") label = "No active music"
delay(1500)
}
}
return label to playing
}
@Composable
private fun MediaCard(modifier: Modifier, dense: Boolean = false, preferred: String? = null, surfaceDisplayId: Int? = null, mediaApps: List<AppEntry> = emptyList(), onOpenPicker: () -> Unit = {}) {
val p = LocalAmni.current
val ctx = LocalContext.current
val np by MediaSessionBridge.nowPlaying.collectAsState()
LaunchedEffect(Unit) { MediaSessionBridge.start(ctx) }
val (src, fallbackPlaying) = rememberMediaSource(ctx)
val playing = np?.playing ?: fallbackPlaying
val title = np?.title?.takeIf { it.isNotBlank() } ?: preferred?.takeIf { it.isNotBlank() } ?: src
val subtitle = np?.let { listOf(it.artist, it.appLabel).filter { s -> s.isNotBlank() }.joinToString(" · ") } ?: ""
val hasAccess = remember { MediaSessionBridge.hasAccess(ctx) }
val pad = if (dense) 10.dp else 16.dp
Box(modifier) {
GlassCard(Modifier.fillMaxSize()) {
Column(Modifier.fillMaxSize().padding(pad), horizontalAlignment = Alignment.CenterHorizontally) {
Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
AccentBar(Modifier.width(if (dense) 18.dp else 28.dp).height(3.dp))
Spacer(Modifier.width(8.dp))
Text("NOW PLAYING", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = if (dense) 8.sp else 10.sp, letterSpacing = 1.sp)
Spacer(Modifier.weight(1f))
Text("CHANGE", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onOpenPicker() })
}
Spacer(Modifier.weight(1f))
Box(
Modifier.size(if (dense) 56.dp else 88.dp).clip(RoundedCornerShape(if (dense) 16.dp else 24.dp)).background(p.accDim).border(1.dp, p.acc.copy(alpha = 0.25f), RoundedCornerShape(if (dense) 16.dp else 24.dp))
.clickable { onOpenPicker() },
contentAlignment = Alignment.Center
) {
val art = np?.art
if (art != null) Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
else Icon(Icons.Rounded.MusicNote, null, Modifier.size(if (dense) 28.dp else 42.dp), tint = p.acc)
}
Spacer(Modifier.height(if (dense) 8.dp else 12.dp))
Text(title, color = p.ink, fontSize = if (dense) 14.sp else 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { onOpenPicker() })
Text(
when {
subtitle.isNotBlank() -> subtitle
!hasAccess -> "Grant media access for track info"
playing -> "Playing · tap art to switch source"
mediaApps.isNotEmpty() -> "Tap art · pick Spotify / music app"
surfaceDisplayId != null -> "Dash panel · media keys"
else -> "Tap art to choose source"
},
color = p.dim, fontSize = if (dense) 10.sp else 12.sp, textAlign = TextAlign.Center, maxLines = 2
)
if (!hasAccess && surfaceDisplayId == null) {
Spacer(Modifier.height(6.dp))
Box(Modifier.clip(RoundedCornerShape(99.dp)).background(p.accDim).border(1.dp, p.acc.copy(alpha = 0.4f), RoundedCornerShape(99.dp)).clickable { MediaSessionBridge.requestAccess(ctx) }.padding(horizontal = 10.dp, vertical = 5.dp)) {
Text("ENABLE MEDIA ACCESS", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold)
}
}
Spacer(Modifier.height(if (dense) 10.dp else 16.dp))
Row(horizontalArrangement = Arrangement.spacedBy(if (dense) 8.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
MediaCtrl(Icons.Rounded.SkipPrevious, "Prev", dense) { if (!MediaSessionBridge.prev()) mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
Box(
Modifier.size(if (dense) 48.dp else 56.dp).clip(CircleShape).background(p.accentBrush)
.clickable { if (!MediaSessionBridge.playPause()) mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) },
contentAlignment = Alignment.Center
) {
Text(if (playing) "❚❚" else "▶", color = if (p.isLight) Color.White else Color(0xFF05070A), fontSize = if (dense) 16.sp else 18.sp, fontWeight = FontWeight.Bold)
}
MediaCtrl(Icons.Rounded.SkipNext, "Next", dense) { if (!MediaSessionBridge.next()) mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_NEXT) }
}
Spacer(Modifier.height(if (dense) 8.dp else 12.dp))
Box(
Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, p.acc.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
.clickable { if (!MediaSessionBridge.play()) mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PLAY) }
.padding(horizontal = if (dense) 12.dp else 18.dp, vertical = if (dense) 8.dp else 10.dp)
) {
Text("PLAY / RESUME", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = if (dense) 9.sp else 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
}
Spacer(Modifier.weight(1f))
Text("Tap music tile to change source", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 8.sp, textAlign = TextAlign.Center)
}
}
}
}
@Composable
private fun MediaSourceSheet(apps: List<AppEntry>, onClose: () -> Unit, onPick: (AppEntry) -> Unit) {
val p = LocalAmni.current
Box(Modifier.fillMaxSize().background(p.scrim).clickable { onClose() }, contentAlignment = Alignment.Center) {
SoftSurface(Modifier.fillMaxWidth(0.88f).padding(10.dp), radius = 18.dp) {
Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
Row(verticalAlignment = Alignment.CenterVertically) {
Text("MUSIC SOURCE", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
Spacer(Modifier.weight(1f))
Text("✕", color = p.dim, modifier = Modifier.clickable { onClose() })
}
if (apps.isEmpty()) Text("No media apps found · open Apps grid", color = p.soft, fontSize = 12.sp)
apps.take(8).forEach { app ->
Row(
Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(p.panel2)
.clickable { onPick(app) }.padding(horizontal = 12.dp),
verticalAlignment = Alignment.CenterVertically
) {
Icon(Icons.Rounded.MusicNote, null, Modifier.size(22.dp), tint = p.acc)
Spacer(Modifier.width(10.dp))
Text(app.label, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
}
}
}
}
}
}
@Composable
private fun MediaCtrl(icon: ImageVector, label: String, dense: Boolean, onClick: () -> Unit) {
val p = LocalAmni.current
Box(
Modifier.size(if (dense) 40.dp else 48.dp).clip(CircleShape).background(p.panel2).border(1.dp, p.hairline, CircleShape).clickable { onClick() },
contentAlignment = Alignment.Center
) {
Icon(icon, label, Modifier.size(if (dense) 20.dp else 24.dp), tint = p.soft)
}
}
@Composable
private fun MediaMiniBar(modifier: Modifier) {
val p = LocalAmni.current
val ctx = LocalContext.current
val (src, playing) = rememberMediaSource(ctx)
Row(modifier.clip(RoundedCornerShape(10.dp)).background(p.panel2).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
Text(if (playing) "▶" else "❚", color = p.acc, fontSize = 11.sp, modifier = Modifier.clickable { mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) })
Text(src, color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
}
}
@Composable
private fun WeatherCard(w: WeatherNow?, dense: Boolean = false) {
val p = LocalAmni.current
val pad = if (dense) 8.dp else 14.dp
GlassCard(Modifier.fillMaxWidth()) {
Column(Modifier.padding(pad)) {
Row(verticalAlignment = Alignment.CenterVertically) {
AccentBar(Modifier.width(if (dense) 16.dp else 24.dp).height(3.dp))
Spacer(Modifier.width(6.dp))
Text("WEATHER", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = if (dense) 8.sp else 10.sp, letterSpacing = 1.sp)
}
Row(Modifier.padding(top = if (dense) 4.dp else 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (dense) 6.dp else 12.dp)) {
Text(w?.emoji ?: "🌡️", fontSize = if (dense) 22.sp else 34.sp)
Text("${w?.tempF ?: "--"}°", color = p.ink, fontFamily = FontFamily.Monospace, fontSize = if (dense) 22.sp else 32.sp, fontWeight = FontWeight.Bold)
if (!dense) Column {
Text(w?.label ?: "Waiting for GPS…", color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
Text("WIND ${w?.windMph ?: "--"} · HUM ${w?.humidity ?: "--"}%", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
} else Text(w?.label ?: "GPS…", color = p.dim, fontSize = 10.sp, maxLines = 1, modifier = Modifier.weight(1f))
}
}
}
}
@Composable
private fun TurnBanner(cue: TurnGuidance.Cue, next: RouteStep?, voiceOn: Boolean, onToggleVoice: () -> Unit, dense: Boolean, modifier: Modifier = Modifier) {
val p = LocalAmni.current
GlassCard(modifier) {
Row(Modifier.padding(horizontal = if (dense) 10.dp else 16.dp, vertical = if (dense) 6.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) {
Text(cue.arrow, color = p.acc, fontFamily = FontFamily.Monospace, fontSize = if (dense) 22.sp else 30.sp, fontWeight = FontWeight.Bold)
Spacer(Modifier.width(if (dense) 8.dp else 12.dp))
Column {
Text(TurnGuidance.distText(cue.distM), color = p.acc, fontFamily = FontFamily.Monospace, fontSize = if (dense) 14.sp else 18.sp, fontWeight = FontWeight.Bold)
Text(TurnGuidance.phrase(cue.step.type, cue.step.modifier, cue.step.name), color = p.soft, fontFamily = FontFamily.Monospace, fontSize = if (dense) 10.sp else 13.sp, maxLines = 1)
}
if (next != null && cue.distM < 400) {
Spacer(Modifier.width(if (dense) 8.dp else 12.dp))
Box(Modifier.clip(RoundedCornerShape(99.dp)).background(p.accDim).padding(horizontal = 8.dp, vertical = 3.dp)) {
Text("then ${TurnGuidance.arrowFor(next.type, next.modifier)}", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = if (dense) 10.sp else 12.sp, fontWeight = FontWeight.Bold)
}
}
Spacer(Modifier.width(if (dense) 10.dp else 16.dp))
Text(if (voiceOn) "🔊" else "🔇", fontSize = if (dense) 14.sp else 18.sp, modifier = Modifier.clickable { onToggleVoice() })
}
}
}
@Composable
private fun TripCard(speedMph: Int, route: RouteResult?, dense: Boolean = false) {
val p = LocalAmni.current
val pad = if (dense) 8.dp else 14.dp
GlassCard(Modifier.fillMaxWidth()) {
Column(Modifier.padding(pad)) {
Row(verticalAlignment = Alignment.CenterVertically) {
AccentBar(Modifier.width(if (dense) 16.dp else 24.dp).height(3.dp))
Spacer(Modifier.width(6.dp))
Text("TRIP", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = if (dense) 8.sp else 10.sp, letterSpacing = 1.sp)
}
Row(Modifier.padding(top = if (dense) 4.dp else 8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
Text(if (route != null) "${(route.durationS / 60).roundToInt()}m" else "$speedMph", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = if (dense) 22.sp else 30.sp, fontWeight = FontWeight.Bold)
Text(if (route != null) "%.1f mi · ${LocalTime.now().plusSeconds(route.durationS.toLong()).format(DateTimeFormatter.ofPattern("h:mm a"))}".format(route.distanceM / 1609.34) else "MPH", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = if (dense) 10.sp else 12.sp, modifier = Modifier.padding(bottom = 3.dp))
}
if (!dense) Text(if (route != null) "Search or long-press to add stops" else "Search or long-press the map to go", color = p.dim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
}
}
}
@Composable
private fun GateHintCard(cfg: DriveConfig, dense: Boolean = false) {
val p = LocalAmni.current
val on = cfg.masterGateEnabled && cfg.driverGateEnabled
val pad = if (dense) 8.dp else 14.dp
GlassCard(Modifier.fillMaxWidth()) {
Column(Modifier.padding(pad)) {
Row(verticalAlignment = Alignment.CenterVertically) {
AccentBar(Modifier.width(if (dense) 16.dp else 24.dp).height(3.dp))
Spacer(Modifier.width(6.dp))
Text("GATE", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = if (dense) 8.sp else 10.sp, letterSpacing = 1.sp)
Spacer(Modifier.weight(1f))
Chip(if (on) "ON" else "OFF", if (on) p.acc else p.err)
}
if (!dense) Text(
if (on) "Video & games pause up front while moving. Rear never gates."
else "Everything unlocked this drive. Re-arms next start.",
color = p.soft, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp)
) else Text(if (on) "Moving locks video" else "Unlocked", color = p.soft, fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
}
}
}
@Composable
private fun GateSheet(app: AppEntry, rear: Boolean, onRear: () -> Unit, onPassenger: () -> Unit, onDismiss: () -> Unit) {
val p = LocalAmni.current
Box(Modifier.fillMaxSize().background(p.scrim).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
GlassCard(Modifier.width(500.dp)) {
Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
Box(Modifier.size(64.dp).clip(CircleShape).background(p.err.copy(alpha = 0.12f)).border(1.dp, p.err.copy(alpha = 0.45f), CircleShape), contentAlignment = Alignment.Center) {
Icon(Icons.Rounded.Lock, null, Modifier.size(28.dp), tint = p.err)
}
Text("${app.label} is gated while moving", color = p.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
Text("Video pauses on the driver screen when moving. Send it rear — crew watches, you drive.", color = p.soft, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
if (rear) Box(Modifier.clip(RoundedCornerShape(14.dp)).background(p.accentBrush).clickable { onRear() }.padding(horizontal = 18.dp, vertical = 14.dp)) {
Text("SEND TO REAR →", color = Color(0xFF05070A), fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}
Box(Modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, p.acc, RoundedCornerShape(14.dp)).clickable { onPassenger() }.padding(horizontal = 18.dp, vertical = 14.dp)) {
Text("I'M A PASSENGER", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}
Box(Modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, p.line2, RoundedCornerShape(14.dp)).clickable { onDismiss() }.padding(horizontal = 18.dp, vertical = 14.dp)) {
Text("CANCEL", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}
}
Text("No hard locks · gate off = one tap in the dock", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 0.5.sp)
}
}
}
}
