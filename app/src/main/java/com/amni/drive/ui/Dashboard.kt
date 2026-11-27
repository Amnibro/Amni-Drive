package com.amni.drive.ui
import android.content.Context
import android.content.Intent
import android.location.Location
import android.media.AudioManager
import android.net.ConnectivityManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.amni.drive.map.Geocoder
import com.amni.drive.map.VoiceGuide
import com.amni.drive.map.rememberMapCamera
import com.amni.drive.voice.VoiceSearch
import com.amni.drive.media.IncomingMsg
import com.amni.drive.media.MediaBrowserHub
import com.amni.drive.media.MediaSessionBridge
import com.amni.drive.media.MessageFeed
import com.amni.drive.media.NativeMediaAppTile
import com.amni.drive.weather.WeatherNow
import com.amni.drive.weather.Wx
import com.amni.drive.wire.AaAudioStreamer
import com.amni.drive.wire.AudioCaptureGrantActivity
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
onOpenTutorial: () -> Unit = {},
initialPage: DrivePage = DrivePage.HOME,
carSurface: Boolean = false,
surfaceDisplayId: Int? = null,
onSetTileLayout: (TileLayout) -> Unit = {},
onSetWidgets: (List<DashWidget>) -> Unit = {},
onSetWidgetWidthMode: (Int) -> Unit = {},
onSetWidgetCompact: (Boolean) -> Unit = {},
onSetDockPosition: (Int) -> Unit = {},
onSetMapSplitRatio: (Float) -> Unit = {},
onSetWidgetRatio: (Float) -> Unit = {},
onSetSwapMapMedia: (Boolean) -> Unit = {},
onSetCarUiScale: (Float) -> Unit = {},
onSetFontScale: (Float) -> Unit = {},
onCycleTheme: () -> Unit = {}
) {
val p = rememberPalette(theme)
val ctxRoot = LocalContext.current
CompositionLocalProvider(LocalAmni provides p) {
var page by remember { mutableStateOf(initialPage) }
LaunchedEffect(initialPage) { page = initialPage }
var expandedTile by remember { mutableStateOf<String?>(null) }
LaunchedEffect(page) { if (page != DrivePage.HOME) expandedTile = null }
var menuOpen by remember { mutableStateOf(false) }
var layoutPicker by remember { mutableStateOf(false) }
var widgetPicker by remember { mutableStateOf(false) }
var mapStudioOpen by remember { mutableStateOf(false) }
var blocked by remember { mutableStateOf<AppEntry?>(null) }
var mediaHint by remember { mutableStateOf<String?>(null) }
var launchHint by remember { mutableStateOf<String?>(null) }
var mediaPicker by remember { mutableStateOf(false) }
var embeddedMediaApp by remember { mutableStateOf<AppEntry?>(null) }
var embeddedWebApp by remember { mutableStateOf<WebAppEntry?>(null) }
var autoRegisteredMedia by remember { mutableStateOf(false) }
val dashKbHost = remember { DashKeyboardHost() }
val mapStudioPrefs by AmniMapStudio.prefs.collectAsState()
LaunchedEffect(p.isLight, mapStudioPrefs) {
AmniMapStudio.ensureLoaded(ctxRoot)
AmniMapStudio.syncHostTheme(ctxRoot, p.isLight)
}
val liveNp by MediaSessionBridge.nowPlaying.collectAsState()
LaunchedEffect(apps, liveNp?.pkg) {
MediaSessionBridge.ensureStarted(ctxRoot)
if (!autoRegisteredMedia && embeddedMediaApp == null && embeddedWebApp == null && apps.isNotEmpty()) {
val activePkg = liveNp?.pkg
val preferred = (if (!activePkg.isNullOrBlank()) apps.firstOrNull { it.packageName.equals(activePkg, true) } else null)
?: apps.firstOrNull { it.packageName.equals("com.spotify.music", true) }
?: apps.firstOrNull { it.packageName.equals("com.google.android.apps.youtube.music", true) }
?: apps.firstOrNull { it.packageName.equals("com.apple.android.music", true) }
if (preferred != null) {
embeddedMediaApp = preferred
mediaHint = preferred.label
autoRegisteredMedia = true
}
}
}
val hasAmniMap = remember(apps) { com.amni.drive.launcher.CarEmbed.supports(ctxRoot, com.amni.drive.launcher.CarEmbed.MAP_PKG) }
var useAmniMap by remember(carSurface, hasAmniMap) { mutableStateOf(carSurface && hasAmniMap) }
val navScope = rememberCoroutineScope()
LaunchedEffect(launchHint) { if (launchHint != null) { delay(3200); launchHint = null } }
val mediaAppsList = remember(apps) {
apps.filter {
it.category == com.amni.drive.gate.AppCategory.MEDIA ||
it.category == com.amni.drive.gate.AppCategory.VIDEO ||
MediaBrowserHub.isAudioApp(ctxRoot, it.packageName) ||
it.packageName.contains("youtube", true) ||
it.packageName.contains("netflix", true) ||
it.packageName.contains("disney", true) ||
it.packageName.contains("hulu", true) ||
it.packageName.contains("plex", true) ||
it.packageName.contains("twitch", true) ||
it.packageName.contains("prime", true)
}
}
val openWebInMediaBox: (WebAppEntry) -> Unit = { web ->
autoRegisteredMedia = true
embeddedMediaApp = null
embeddedWebApp = web
mediaHint = web.label
page = DrivePage.HOME
launchHint = "${web.emoji} ${web.label}"
}
val openInMediaBox: (AppEntry) -> Unit = { app ->
if (app.packageName == com.amni.drive.launcher.CarEmbed.MAP_PKG && carSurface && hasAmniMap) {
useAmniMap = true
page = DrivePage.HOME
launchHint = "Amni-Map active"
} else {
val isAudio = MediaBrowserHub.isAudioApp(ctxRoot, app.packageName) || app.category == com.amni.drive.gate.AppCategory.MEDIA
if (isAudio) {
autoRegisteredMedia = true
embeddedWebApp = null
embeddedMediaApp = app
mediaHint = app.label
page = DrivePage.HOME
MediaBrowserHub.wakeAndPlay(ctxRoot, app.packageName)
launchHint = "▶ ${app.label} (Signed In)"
} else {
val d = onLaunch(app)
if (!d.allow) {
blocked = app
} else {
autoRegisteredMedia = true
mediaHint = app.label
if (carSurface) {
val webMatch = if (!com.amni.drive.launcher.CarApps.ready.value) WebAppEntry.matchPackage(app.packageName) else null
if (webMatch != null) {
embeddedMediaApp = null
embeddedWebApp = webMatch
} else {
embeddedWebApp = null
embeddedMediaApp = app
}
page = DrivePage.HOME
}
launchHint = "▶ ${app.label}"
}
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
LaunchedEffect(theme.basemap, p.isLight, mapStudioPrefs.headingUp, mapStudioPrefs.perspective3d) {
camera.basemap = if (theme.basemap < 0) (if (p.isLight) 1 else 0) else theme.basemap
camera.northUp = !mapStudioPrefs.headingUp
camera.tilt = mapStudioPrefs.perspective3d
}
val moving = driveState == DriveState.MOVING || driveState == DriveState.UNKNOWN
val speedMph = ((location?.speed ?: 0f) * 2.23694f).roundToInt()
val addPoint: (Double, Double) -> Unit = { la, lo -> if (stops.isEmpty()) stops.add(RoutePoint(la, lo)) else stops.add(stops.size - 1, RoutePoint(la, lo)) }
val onGo: (Place) -> Unit = { stops.clear(); stops.add(RoutePoint(it.lat, it.lon)); AaNavState.destination = it }
val onAddStop: (Place) -> Unit = { if (stops.isEmpty()) stops.add(RoutePoint(it.lat, it.lon)) else stops.add(stops.size - 1, RoutePoint(it.lat, it.lon)) }
val onEnd: () -> Unit = { stops.clear(); route = null; AaNavState.destination = null; AaNavState.route = null }
val stopsKey = stops.joinToString("|") { "${it.lat},${it.lon}" }
LaunchedEffect(stopsKey, location == null) {
if (stops.isEmpty()) { route = null; return@LaunchedEffect }
var curLoc = location
if (curLoc == null) {
(ctxRoot.applicationContext as? com.amni.drive.AmniDriveApp)?.let { appRef ->
appRef.tracker.start()
repeat(6) {
if (appRef.tracker.location.value != null) return@repeat
delay(250)
}
curLoc = appRef.tracker.location.value
}
}
val origin = if (curLoc != null) RoutePoint(curLoc!!.latitude, curLoc!!.longitude) else RoutePoint(camera.lat, camera.lon)
routing = true
route = OsrmRouting.route(listOf(origin) + stops.toList())
AaNavState.route = route
if (curLoc == null && stops.isNotEmpty()) {
camera.follow = false
camera.lat = stops.last().lat
camera.lon = stops.last().lon
}
routing = false
}
val voice = remember { VoiceGuide(ctxRoot) }
DisposableEffect(Unit) { onDispose { voice.shutdown() } }
var stepIdx by remember { mutableStateOf(0) }
LaunchedEffect(route) { stepIdx = 0; voice.reset() }
val cue = remember(route, location, stepIdx) { route?.let { r -> location?.let { l -> TurnGuidance.cue(r, l.latitude, l.longitude, stepIdx) } } }
LaunchedEffect(cue) { cue?.let { c -> if (c.idx != stepIdx) stepIdx = c.idx; voice.maybeSpeak(c, cfg.voiceNav) } }
val onToggleVoice: () -> Unit = { navScope.launch { ConfigStore(ctxRoot).setVoiceNav(!cfg.voiceNav) } }
var voiceHint by remember { mutableStateOf<String?>(null) }
var orbKbOpen by remember { mutableStateOf(false) }
var orbKbPreview by remember { mutableStateOf("") }
val startVoice: () -> Unit = {
VoiceSearch.listen(ctxRoot, { voiceHint = it }, { text ->
navScope.launch {
if (text == null) { delay(3200); voiceHint = null } else {
voiceHint = "Searching “$text”…"
val top = runCatching { Geocoder.search(text, location?.latitude, location?.longitude).firstOrNull() }.getOrNull()
if (top == null) { voiceHint = "No match for “$text”"; delay(2800); voiceHint = null } else {
useAmniMap = false
page = DrivePage.NAV
onGo(top)
voice.say("Routing to ${top.name.ifBlank { "destination" }}", cfg.voiceNav)
voiceHint = null
}
}
}
})
}
val micRegistry = androidx.activity.compose.LocalActivityResultRegistryOwner.current
val micPerm = if (micRegistry != null) rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) startVoice() else voiceHint = "Mic permission needed" } else null
val onVoice: () -> Unit = {
if (VoiceSearch.hasMicPermission(ctxRoot)) startVoice()
else micPerm?.launch(android.Manifest.permission.RECORD_AUDIO) ?: run { voiceHint = "Grant Microphone in Amni-Drive Settings → WIRE on phone"; navScope.launch { delay(4000); voiceHint = null } }
}
DisposableEffect(Unit) { onDispose { VoiceSearch.stop() } }
val incoming by MessageFeed.latest.collectAsState()
LaunchedEffect(incoming) {
incoming?.let { m ->
if (cfg.msgAutoRead) voice.say("Message from ${m.sender}. ${m.text}", true)
delay(12000)
MessageFeed.dismiss()
}
}
val pendingGo by AaNavState.pendingGo.collectAsState()
LaunchedEffect(pendingGo) { pendingGo?.let { g -> useAmniMap = false; page = DrivePage.NAV; onGo(g); AaNavState.consumeGo() } }
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
val anyOverlayOpen = menuOpen || layoutPicker || widgetPicker || mapStudioOpen || mediaPicker || orbKbOpen || dashKbHost.active != null || blocked != null || showLayers || showReport
SideEffect { com.amni.drive.launcher.CarEmbed.overlayBlocking = anyOverlayOpen }
DisposableEffect(Unit) { onDispose { com.amni.drive.launcher.CarEmbed.overlayBlocking = false } }
CompositionLocalProvider(LocalDashKeyboard provides dashKbHost) {
BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(p.bg, if (p.isLight) Color(0xFFE8ECF0) else Color(0xFF05070A))))) {
val isPortraitPhone = !carSurface && maxHeight > maxWidth * 1.15f
val aspect = maxWidth / maxHeight
val micro = maxWidth < 520.dp || maxHeight < 220.dp
val dense = carSurface || maxHeight < 560.dp || aspect > 1.5f || maxWidth < 960.dp
val bottomDock = theme.dockPosition == 1 || isPortraitPhone
val railW = if (bottomDock) 0.dp else when {
micro -> 48.dp
maxWidth < 720.dp -> 54.dp
dense -> 60.dp
else -> 92.dp
}
val pad = when { micro -> 3.dp; dense -> 4.dp; else -> 12.dp }
val gap = when { micro -> 3.dp; dense -> 5.dp; else -> 10.dp }
val contentW = maxWidth - railW
val baseWidgetW = when (theme.widgetWidthMode) {
0 -> 130.dp
2 -> 212.dp
else -> 166.dp
}
val widgetW = when {
contentW < 640.dp -> baseWidgetW.coerceAtMost(140.dp)
dense -> baseWidgetW
else -> (baseWidgetW + 44.dp)
}
val showTopBar = !carSurface && maxHeight > 480.dp && (!micro || isPortraitPhone)
val mediaPulse by AaNavState.mediaPulse.collectAsState()
var mediaFlash by remember { mutableStateOf(false) }
LaunchedEffect(mediaPulse) { if (mediaPulse > 0L) { mediaFlash = true; page = DrivePage.HOME; delay(1200); mediaFlash = false } }
val layout = resolveTileLayout(theme.tileLayout, micro && !isPortraitPhone, dense, contentW, maxHeight, carSurface)
if (theme.weatherBg && !micro && !carSurface) WeatherLayer(weather?.wx ?: Wx.NONE, moving, Modifier.fillMaxSize())
Row(Modifier.fillMaxSize()) {
if (!bottomDock) {
Rail(page, cfg.driverGateEnabled && cfg.masterGateEnabled, { menuOpen = true }, { page = it; expandedTile = null }, onToggleGate, railW, dense || micro)
}
Column(Modifier.weight(1f).fillMaxHeight()) {
if (showTopBar) TopBar(driveState, speedMph, weather, passenger)
else if (carSurface || (dense && !micro)) CarStatusStrip(driveState, speedMph, weather, passenger, 0.dp, voiceActive = voiceHint != null, onVoice = onVoice, onOpenLayout = { layoutPicker = true }, onOpenMedia = { mediaPicker = true }, onOpenWidgets = { widgetPicker = true })
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
openInMediaBox(app)
}
DrivePage.NAV -> Box(Modifier.fillMaxSize().padding(pad)) {
if (useAmniMap && carSurface && hasAmniMap) {
AmniMapEmbedTile(Modifier.fillMaxSize(), isExpanded = true, carUiScale = theme.carUiScale, onToggleExpand = { page = DrivePage.HOME }, onOpenStudio = { mapStudioOpen = true }) { useAmniMap = false }
} else {
MapCard(Modifier.fillMaxSize(), camera, tiles, route, reports, stops, location, routing, true, netOk, showLayers, showReport, { showLayers = it }, { showReport = it }, reportStore, addPoint, onGo, onAddStop, onEnd, dense || micro, if (carSurface) ({ AaNavState.openHostSearch() }) else null, onVoice, isExpanded = true, onToggleExpand = { page = DrivePage.HOME })
}
}
DrivePage.HOME -> HomeTiles(
layout = layout,
pad = pad,
gap = gap,
widgetW = widgetW,
dense = dense || micro,
widgetCompact = theme.widgetCompact,
carSurface = carSurface,
carTopPad = 0.dp,
mediaFlash = mediaFlash,
mediaHint = mediaHint,
surfaceDisplayId = surfaceDisplayId,
useAmniMap = useAmniMap && hasAmniMap,
hasAmniMap = hasAmniMap,
onToggleAmniMap = { useAmniMap = !useAmniMap },
onOpenMapStudio = { mapStudioOpen = true },
embeddedMediaApp = embeddedMediaApp,
embeddedWebApp = embeddedWebApp,
onOpenWebFallback = openWebInMediaBox,
onClearMediaApp = { autoRegisteredMedia = true; embeddedMediaApp = null; embeddedWebApp = null },
widgetsList = theme.widgets,
onSetWidgets = onSetWidgets,
onEditWidgets = { widgetPicker = true },
onToggleGate = onToggleGate,
allApps = apps,
onQuickLaunch = openInMediaBox,
expandedTile = expandedTile,
onToggleExpand = { id -> expandedTile = if (expandedTile == id) null else id },
mapSplitRatio = theme.mapSplitRatio,
widgetRatio = theme.widgetRatio,
swapMapMedia = theme.swapMapMedia,
onSetMapSplitRatio = onSetMapSplitRatio,
onSetWidgetRatio = onSetWidgetRatio,
onToggleSwapMapMedia = { onSetSwapMapMedia(!theme.swapMapMedia) },
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
hostKeyboard = null,
onVoice = onVoice,
onOpenOrbKb = { orbKbOpen = true },
carUiScale = theme.carUiScale,
fontScale = theme.fontScale
)
}
}
cue?.let { c -> if (page != DrivePage.APPS && !useAmniMap) TurnBanner(c, route?.steps?.getOrNull(c.idx + 1), cfg.voiceNav, onToggleVoice, dense || micro, Modifier.align(Alignment.TopCenter).padding(top = if (carSurface) 7.dp else 8.dp)) }
incoming?.let { m -> if (cfg.msgBanner) MsgBanner(m, { voice.say("Message from ${m.sender}. ${m.text}", true) }, { MessageFeed.dismiss() }, dense || micro, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)) }
val toastText = voiceHint?.let { "🎤 $it" } ?: launchHint
toastText?.let { h ->
Box(Modifier.align(Alignment.BottomCenter).padding(bottom = if (incoming != null && cfg.msgBanner) 64.dp else 10.dp).clip(RoundedCornerShape(99.dp)).background(if (p.isLight) Color(0xF8FFFFFF) else Color(0xF512151C)).border(1.5.dp, p.acc.copy(alpha = 0.7f), RoundedCornerShape(99.dp)).padding(horizontal = 16.dp, vertical = 8.dp)) {
Text(h, color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
}
}
}
if (bottomDock) {
BottomCenterDock(
page = page,
gateOn = cfg.driverGateEnabled && cfg.masterGateEnabled,
compact = dense || micro,
onMenu = { menuOpen = true },
onPage = { page = it; expandedTile = null },
onMedia = { mediaPicker = true },
onLayout = { layoutPicker = true },
onWidgets = { widgetPicker = true },
onToggleGate = onToggleGate
)
}
}
}
if (menuOpen) MenuDrawer(
page = page,
rearAvailable = rearAvailable,
gateOn = cfg.driverGateEnabled && cfg.masterGateEnabled,
weatherOn = theme.weatherBg,
carSurface = carSurface,
tileLayout = theme.tileLayout,
dockPosition = theme.dockPosition,
swapMapMedia = theme.swapMapMedia,
carUiScale = theme.carUiScale,
fontScale = theme.fontScale,
onPage = { page = it; expandedTile = null; menuOpen = false },
onToggleGate = onToggleGate,
onToggleWeather = onToggleWeather,
onSettings = { menuOpen = false; onOpenSettings() },
onClose = { menuOpen = false },
onMedia = { menuOpen = false; mediaPicker = true },
onTileLayout = { menuOpen = false; layoutPicker = true },
onWidgets = { menuOpen = false; widgetPicker = true },
onMapStudio = { menuOpen = false; mapStudioOpen = true },
onReportBug = { menuOpen = false; CrashReportActivity.openBugReporter(ctxRoot); if (carSurface) launchHint = "🐞 Scrubbed bug report opened on phone" },
onTutorial = { menuOpen = false; onOpenTutorial() },
onToggleDock = { onSetDockPosition(if (theme.dockPosition == 0) 1 else 0) },
onToggleSwap = { onSetSwapMapMedia(!theme.swapMapMedia) },
onSetCarUiScale = onSetCarUiScale,
onSetFontScale = onSetFontScale,
themeLabel = theme.preset.label,
onTheme = onCycleTheme
)
if (layoutPicker) TileLayoutPicker(
selected = theme.tileLayout,
effective = layout,
dockPosition = theme.dockPosition,
swapMapMedia = theme.swapMapMedia,
carUiScale = theme.carUiScale,
fontScale = theme.fontScale,
onPick = { onSetTileLayout(it); layoutPicker = false },
onSetDock = onSetDockPosition,
onToggleSwap = { onSetSwapMapMedia(!theme.swapMapMedia) },
onSetCarUiScale = onSetCarUiScale,
onSetFontScale = onSetFontScale,
onClose = { layoutPicker = false },
compact = carSurface
)
if (widgetPicker) WidgetPickerSheet(theme.widgets, theme.widgetWidthMode, theme.widgetCompact, onSetWidgets, onSetWidgetWidthMode, onSetWidgetCompact) { widgetPicker = false }
if (mapStudioOpen) AmniMapStudioSheet(ctxRoot, mapStudioPrefs, p.isLight) { mapStudioOpen = false }
if (mediaPicker) MediaSourceSheet(mediaAppsList, apps, { mediaPicker = false }, { openInMediaBox(it); mediaPicker = false }, { openWebInMediaBox(it); mediaPicker = false })
dashKbHost.active?.let { kb ->
BoxWithConstraints(
Modifier.fillMaxSize().background(p.scrim.copy(alpha = 0.35f)).clickable { kb.onClose() },
contentAlignment = Alignment.BottomCenter
) {
Box(
Modifier
.fillMaxWidth(if (maxWidth > 720.dp) 0.94f else 0.99f)
.padding(horizontal = 4.dp, vertical = 5.dp)
.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
) {
CarTouchKeyboard(
preview = kb.preview,
voiceStatus = kb.voiceStatus,
placeholder = kb.placeholder,
enterLabel = kb.enterLabel,
isMapMode = kb.isMapMode,
onText = kb.onText,
onBackspace = kb.onBackspace,
onClear = kb.onClear,
onEnter = kb.onEnter,
onAutoGo = kb.onAutoGo,
onVoice = kb.onVoice,
onClose = kb.onClose
)
}
}
}
if (orbKbOpen) {
Box(
Modifier.fillMaxSize().background(p.scrim.copy(alpha = 0.45f)).clickable { orbKbOpen = false },
contentAlignment = Alignment.BottomCenter
) {
Box(
Modifier.fillMaxWidth(0.95f).padding(bottom = 6.dp)
.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
) {
CarTouchKeyboard(
preview = orbKbPreview,
voiceStatus = voiceHint,
placeholder = "Search place or address in Amni-Map…",
enterLabel = "FIND ↵",
isMapMode = true,
onText = { txt ->
orbKbPreview = (orbKbPreview + txt).take(80)
com.amni.drive.launcher.CarEmbed.sendQuery(orbKbPreview, submit = false)
},
onBackspace = {
orbKbPreview = orbKbPreview.dropLast(1)
com.amni.drive.launcher.CarEmbed.sendQuery(orbKbPreview, submit = false)
},
onClear = {
orbKbPreview = ""
com.amni.drive.launcher.CarEmbed.sendQuery("", submit = false)
},
onEnter = {
com.amni.drive.launcher.CarEmbed.sendQuery(orbKbPreview, submit = true, autoGo = false)
orbKbOpen = false
},
onAutoGo = {
if (orbKbPreview.isNotBlank()) {
com.amni.drive.launcher.CarEmbed.sendQuery(orbKbPreview, submit = true, autoGo = true)
orbKbOpen = false
}
},
onVoice = {
VoiceSearch.listen(ctxRoot, { voiceHint = it }, { spoken ->
if (!spoken.isNullOrBlank()) {
orbKbPreview = spoken
com.amni.drive.launcher.CarEmbed.sendQuery(spoken, submit = true, autoGo = true)
orbKbOpen = false
voiceHint = "Routing “$spoken”"
navScope.launch { delay(2800); voiceHint = null }
} else {
navScope.launch { delay(3200); voiceHint = null }
}
})
},
onClose = { orbKbOpen = false }
)
}
}
}
blocked?.let { app ->
GateSheet(app, rearAvailable,
onRear = { onLaunchRear(app); blocked = null },
onPassenger = { onConfirmPassenger(); if (onLaunch(app).allow) { embeddedMediaApp = app; blocked = null } },
onDismiss = { blocked = null })
}
}
}
}
}
private fun resolveTileLayout(pref: TileLayout, micro: Boolean, dense: Boolean, contentW: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp, carSurface: Boolean): TileLayout {
if (pref != TileLayout.AUTO) return pref
return when {
micro -> TileLayout.MAP_ONLY
contentW < 640.dp -> TileLayout.MAP_FOCUS
carSurface -> TileLayout.BALANCED
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
private fun DraggableDivider(
totalPx: Float,
onDragFraction: (Float) -> Unit,
onCommit: () -> Unit,
onReset: () -> Unit
) {
val p = LocalAmni.current
var dragging by remember { mutableStateOf(false) }
Box(
Modifier
.width(12.dp)
.fillMaxHeight()
.pointerInput(totalPx) {
detectHorizontalDragGestures(
onDragStart = { dragging = true },
onDragEnd = { dragging = false; onCommit() },
onDragCancel = { dragging = false; onCommit() }
) { change, dragAmount ->
change.consume()
if (totalPx > 40f) onDragFraction(dragAmount / totalPx)
}
}
.pointerInput(Unit) {
detectTapGestures(onDoubleTap = { onReset() })
},
contentAlignment = Alignment.Center
) {
Box(
Modifier
.width(if (dragging) 5.dp else 3.dp)
.height(if (dragging) 56.dp else 36.dp)
.clip(RoundedCornerShape(99.dp))
.background(if (dragging) p.acc else p.hairline.copy(alpha = 0.85f))
)
}
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeTiles(
layout: TileLayout,
pad: androidx.compose.ui.unit.Dp,
gap: androidx.compose.ui.unit.Dp,
widgetW: androidx.compose.ui.unit.Dp,
dense: Boolean,
widgetCompact: Boolean = true,
carSurface: Boolean = false,
carTopPad: androidx.compose.ui.unit.Dp = 0.dp,
mediaFlash: Boolean,
mediaHint: String?,
surfaceDisplayId: Int?,
useAmniMap: Boolean = false,
hasAmniMap: Boolean = false,
onToggleAmniMap: () -> Unit = {},
onOpenMapStudio: () -> Unit = {},
embeddedMediaApp: AppEntry? = null,
embeddedWebApp: WebAppEntry? = null,
onOpenWebFallback: (WebAppEntry) -> Unit = {},
onClearMediaApp: () -> Unit = {},
widgetsList: List<DashWidget> = DashWidget.DEFAULTS,
onSetWidgets: (List<DashWidget>) -> Unit = {},
onEditWidgets: () -> Unit = {},
onToggleGate: () -> Unit = {},
allApps: List<AppEntry> = emptyList(),
onQuickLaunch: (AppEntry) -> Unit = {},
expandedTile: String? = null,
onToggleExpand: (String) -> Unit = {},
mapSplitRatio: Float = 0.54f,
widgetRatio: Float = 0.24f,
swapMapMedia: Boolean = false,
onSetMapSplitRatio: (Float) -> Unit = {},
onSetWidgetRatio: (Float) -> Unit = {},
onToggleSwapMapMedia: () -> Unit = {},
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
hostKeyboard: (() -> Unit)? = null,
onVoice: (() -> Unit)? = null,
onOpenOrbKb: () -> Unit = {},
carUiScale: Float = 1.0f,
fontScale: Float = 1.0f
) {
val p = LocalAmni.current
val ctxHome = LocalContext.current
val dens = LocalDensity.current
val wDense = dense || widgetCompact
var liveMapRatio by remember(mapSplitRatio) { mutableStateOf(mapSplitRatio) }
var liveWidgetRatio by remember(widgetRatio) { mutableStateOf(widgetRatio) }
var editingWidgets by remember { mutableStateOf(false) }
val map: @Composable (Modifier, Boolean) -> Unit = { mod, orb ->
val mapFull = expandedTile == "MAP"
if (useAmniMap && carSurface) {
AmniMapEmbedTile(
modifier = mod,
compactOrb = orb,
isExpanded = mapFull,
carUiScale = carUiScale,
onToggleExpand = if (orb) null else ({ onToggleExpand("MAP") }),
onOpenStudio = if (orb) null else onOpenMapStudio,
onSwitchBuiltIn = onToggleAmniMap
)
} else {
Box(mod) {
MapCard(
modifier = Modifier.fillMaxSize(),
camera = camera,
tiles = tiles,
route = route,
reports = reports,
stops = stops,
location = location,
routing = routing,
fullNav = mapFull,
netOk = netOk,
showLayers = showLayers,
showReport = showReport,
onShowLayers = onShowLayers,
onShowReport = onShowReport,
reportStore = reportStore,
onLongPress = addPoint,
onGo = onGo,
onAddStop = onAddStop,
onEndRoute = onEnd,
dense = dense,
hostKeyboard = hostKeyboard,
onVoice = onVoice,
circular = orb,
isExpanded = mapFull,
onToggleExpand = if (orb) null else ({ onToggleExpand("MAP") })
)
if (hasAmniMap && carSurface && !orb) {
Box(
Modifier.align(Alignment.TopStart).padding(7.dp).clip(RoundedCornerShape(99.dp))
.background(Color(0xD8080B10)).border(1.dp, p.acc.copy(alpha = 0.65f), RoundedCornerShape(99.dp))
.clickable { onToggleAmniMap() }.padding(horizontal = 10.dp, vertical = 4.dp)
) {
Text("USE AMNI-MAP", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}
}
}
}
}
val mediaMod = Modifier.then(if (mediaFlash) Modifier.border(2.dp, p.acc, RoundedCornerShape(18.dp)) else Modifier)
val widgets: @Composable (Modifier) -> Unit = { mod ->
val widgetsFull = expandedTile == "WIDGETS"
Column(mod) {
Row(
Modifier.fillMaxWidth().padding(bottom = 4.dp),
verticalAlignment = Alignment.CenterVertically,
horizontalArrangement = Arrangement.spacedBy(4.dp)
) {
Text(
if (editingWidgets) "DRAG · ✕ REMOVE" else "WIDGETS",
color = if (editingWidgets) p.acc else p.soft,
fontFamily = FontFamily.Monospace,
fontSize = 10.5.sp,
fontWeight = FontWeight.Bold,
letterSpacing = 0.8.sp,
modifier = Modifier.weight(1f),
maxLines = 1,
overflow = TextOverflow.Ellipsis
)
Box(
Modifier.clip(RoundedCornerShape(7.dp))
.background(if (editingWidgets) p.acc else p.panel2)
.border(1.dp, if (editingWidgets) p.acc else p.hairline, RoundedCornerShape(7.dp))
.clickable { editingWidgets = !editingWidgets }
.padding(horizontal = 7.dp, vertical = 3.dp)
) {
Text(
if (editingWidgets) "DONE ✓" else "✎",
color = if (editingWidgets) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink,
fontFamily = FontFamily.Monospace,
fontSize = 10.5.sp,
fontWeight = FontWeight.Bold
)
}
Box(
Modifier.clip(RoundedCornerShape(7.dp))
.background(if (widgetsFull) p.acc else p.panel2)
.border(1.dp, if (widgetsFull) p.acc else p.hairline, RoundedCornerShape(7.dp))
.clickable { onToggleExpand("WIDGETS") }
.padding(horizontal = 7.dp, vertical = 3.dp)
) {
Text(
if (widgetsFull) "⤡" else "⤢",
color = if (widgetsFull) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink,
fontFamily = FontFamily.Monospace,
fontSize = 10.5.sp,
fontWeight = FontWeight.Bold
)
}
}
Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(gap)) {
widgetsList.forEachIndexed { idx, w ->
var dragAccumY by remember(w, widgetsList) { mutableStateOf(0f) }
var dragAccumX by remember(w, widgetsList) { mutableStateOf(0f) }
Box(
Modifier
.fillMaxWidth()
.then(if (editingWidgets) Modifier.border(1.5.dp, p.acc.copy(alpha = 0.8f), RoundedCornerShape(16.dp)) else Modifier)
.combinedClickable(
onClick = { if (!editingWidgets && w == DashWidget.GATE) onToggleGate() },
onLongClick = { editingWidgets = !editingWidgets }
)
.then(
if (editingWidgets) {
Modifier
.pointerInput(w, widgetsList) {
detectVerticalDragGestures(
onDragEnd = { dragAccumY = 0f },
onDragCancel = { dragAccumY = 0f }
) { change, dy ->
change.consume()
dragAccumY += dy
if (dragAccumY > 44f && idx < widgetsList.lastIndex) {
val m = widgetsList.toMutableList()
m[idx] = m[idx + 1].also { m[idx + 1] = m[idx] }
dragAccumY = 0f
onSetWidgets(m)
} else if (dragAccumY < -44f && idx > 0) {
val m = widgetsList.toMutableList()
m[idx] = m[idx - 1].also { m[idx - 1] = m[idx] }
dragAccumY = 0f
onSetWidgets(m)
}
}
}
.pointerInput(w, widgetsList) {
detectHorizontalDragGestures(
onDragEnd = { dragAccumX = 0f },
onDragCancel = { dragAccumX = 0f }
) { change, dx ->
change.consume()
dragAccumX += dx
if (kotlin.math.abs(dragAccumX) > 75f && widgetsList.size > 1) {
dragAccumX = 0f
onSetWidgets(widgetsList - w)
}
}
}
} else Modifier
)
) {
when (w) {
DashWidget.WEATHER -> WeatherCard(weather, wDense)
DashWidget.TRIP -> TripCard(speedMph, route, wDense)
DashWidget.GATE -> GateHintCard(cfg, wDense)
DashWidget.CLOCK -> ClockWidgetCard(wDense)
DashWidget.MEDIA_MINI -> QuickMediaWidgetCard(wDense, onOpenMediaPicker)
DashWidget.SHORTCUTS -> ShortcutsWidgetCard(allApps, wDense, onQuickLaunch)
DashWidget.VEHICLE -> VehicleWidgetCard(speedMph, location != null, netOk, wDense)
DashWidget.COMPASS -> CompassWidgetCard(location, wDense)
}
if (editingWidgets) {
Row(
Modifier
.align(Alignment.TopEnd)
.padding(4.dp)
.clip(RoundedCornerShape(99.dp))
.background(Color(0xE80A0D12))
.border(1.dp, p.acc.copy(alpha = 0.7f), RoundedCornerShape(99.dp))
.padding(horizontal = 4.dp, vertical = 2.dp),
horizontalArrangement = Arrangement.spacedBy(3.dp),
verticalAlignment = Alignment.CenterVertically
) {
Text("≡", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
if (idx > 0) {
Text("▲", color = p.ink, fontSize = 9.sp, modifier = Modifier.clickable {
val m = widgetsList.toMutableList()
m[idx] = m[idx - 1].also { m[idx - 1] = m[idx] }
onSetWidgets(m)
}.padding(horizontal = 2.dp))
}
if (idx < widgetsList.lastIndex) {
Text("▼", color = p.ink, fontSize = 9.sp, modifier = Modifier.clickable {
val m = widgetsList.toMutableList()
m[idx] = m[idx + 1].also { m[idx + 1] = m[idx] }
onSetWidgets(m)
}.padding(horizontal = 2.dp))
}
if (widgetsList.size > 1) {
Text("✕", color = p.err, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onSetWidgets(widgetsList - w) }.padding(horizontal = 3.dp))
}
}
}
}
}
Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
Box(
Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(p.panel2.copy(alpha = 0.82f))
.border(1.dp, p.acc.copy(alpha = 0.45f), RoundedCornerShape(10.dp)).clickable { onEditWidgets() }.padding(vertical = 7.dp),
contentAlignment = Alignment.Center
) {
Text("+ WIDGETS", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
}
Box(
Modifier.clip(RoundedCornerShape(10.dp)).background(p.panel2.copy(alpha = 0.82f))
.border(1.dp, p.hairline, RoundedCornerShape(10.dp)).clickable { onToggleSwapMapMedia() }.padding(horizontal = 8.dp, vertical = 7.dp),
contentAlignment = Alignment.Center
) {
Text("⇄", color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}
}
}
}
}
val media: @Composable (Modifier, Boolean) -> Unit = { mod, d ->
val mediaFull = expandedTile == "MEDIA"
val onToggleMediaFull: () -> Unit = { onToggleExpand("MEDIA") }
when {
embeddedWebApp != null -> EmbeddedWebMediaTile(embeddedWebApp, mod.then(mediaMod), isExpanded = mediaFull, fontScale = fontScale, onToggleExpand = onToggleMediaFull, onChangeApp = onOpenMediaPicker, onCloseToControls = onClearMediaApp)
embeddedMediaApp != null && (MediaBrowserHub.isAudioApp(ctxHome, embeddedMediaApp.packageName) || embeddedMediaApp.category == com.amni.drive.gate.AppCategory.MEDIA) -> NativeMediaAppTile(app = embeddedMediaApp, modifier = mod.then(mediaMod), isExpanded = mediaFull, onToggleExpand = onToggleMediaFull, onOpenWebFallback = WebAppEntry.matchPackage(embeddedMediaApp.packageName)?.let { w -> ({ onOpenWebFallback(w) }) }, onChangeApp = onOpenMediaPicker, onCloseToControls = onClearMediaApp)
embeddedMediaApp != null && carSurface -> EmbeddedAppMediaTile(embeddedMediaApp, mod.then(mediaMod), isExpanded = mediaFull, onToggleExpand = onToggleMediaFull, onChangeApp = onOpenMediaPicker, onCloseToControls = onClearMediaApp)
else -> MediaCard(mod.then(mediaMod), d, mediaHint, surfaceDisplayId, mediaApps, onOpenMediaPicker, isExpanded = mediaFull, onToggleExpand = onToggleMediaFull)
}
}
if (expandedTile != null) {
Box(Modifier.fillMaxSize().padding(pad)) {
when (expandedTile) {
"MAP" -> map(Modifier.fillMaxSize(), false)
"MEDIA" -> media(Modifier.fillMaxSize(), false)
"WIDGETS" -> widgets(Modifier.fillMaxSize())
else -> map(Modifier.fillMaxSize(), false)
}
}
return
}
BoxWithConstraints(Modifier.fillMaxSize()) {
val isPortraitCar = maxHeight > maxWidth * 1.05f
if (isPortraitCar && layout != TileLayout.MAP_ONLY) {
val mapFrac = liveMapRatio.coerceIn(0.36f, 0.72f)
Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.spacedBy(gap)) {
map(Modifier.fillMaxWidth().weight(mapFrac), layout == TileLayout.ORB_CENTER)
if (layout == TileLayout.SPLIT_50) {
media(Modifier.fillMaxWidth().weight(1f - mapFrac), dense)
} else {
Row(Modifier.fillMaxWidth().weight(1f - mapFrac), horizontalArrangement = Arrangement.spacedBy(gap)) {
media(Modifier.weight(0.60f).fillMaxHeight(), dense)
widgets(Modifier.weight(0.40f).fillMaxHeight())
}
}
}
return@BoxWithConstraints
}
when (layout) {
TileLayout.SPLIT_50 -> BoxWithConstraints(Modifier.fillMaxSize().padding(pad)) {
val totalPx = with(dens) { maxWidth.toPx() }
val leftFrac = liveMapRatio.coerceIn(0.25f, 0.75f)
Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
if (!swapMapMedia) {
map(Modifier.weight(leftFrac).fillMaxHeight(), false)
DraggableDivider(totalPx, { d -> liveMapRatio = (liveMapRatio + d).coerceIn(0.25f, 0.75f) }, { onSetMapSplitRatio(liveMapRatio) }, { liveMapRatio = 0.50f; onSetMapSplitRatio(0.50f) })
media(Modifier.weight(1f - leftFrac).fillMaxHeight(), dense)
} else {
media(Modifier.weight(leftFrac).fillMaxHeight(), dense)
DraggableDivider(totalPx, { d -> liveMapRatio = (liveMapRatio + d).coerceIn(0.25f, 0.75f) }, { onSetMapSplitRatio(liveMapRatio) }, { liveMapRatio = 0.50f; onSetMapSplitRatio(0.50f) })
map(Modifier.weight(1f - leftFrac).fillMaxHeight(), false)
}
}
}
TileLayout.ORB_CENTER -> BoxWithConstraints(Modifier.fillMaxSize().padding(pad)) {
val totalPx = with(dens) { maxWidth.toPx() }
val wFrac = liveWidgetRatio.coerceIn(0.16f, 0.30f)
val rem = (1f - wFrac).coerceAtLeast(0.50f)
val orbFrac = (rem * liveMapRatio.coerceIn(0.42f, 0.70f)).coerceAtLeast(0.32f)
val mediaFrac = (rem - orbFrac).coerceAtLeast(0.18f)
Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
widgets(Modifier.weight(wFrac).fillMaxHeight())
DraggableDivider(totalPx, { d -> liveWidgetRatio = (liveWidgetRatio + d).coerceIn(0.16f, 0.30f) }, { onSetWidgetRatio(liveWidgetRatio) }, { liveWidgetRatio = 0.22f; onSetWidgetRatio(0.22f) })
Column(
Modifier.weight(orbFrac).fillMaxHeight().padding(horizontal = 2.dp),
horizontalAlignment = Alignment.CenterHorizontally,
verticalArrangement = Arrangement.Center
) {
BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
val orbSize = (minOf(maxWidth, maxHeight) - 4.dp).coerceAtLeast(150.dp)
Box(
Modifier.size(orbSize).clip(CircleShape)
.background(p.bg)
.border(2.5.dp, p.accentBrush, CircleShape)
.padding(3.dp),
contentAlignment = Alignment.Center
) {
Box(Modifier.fillMaxSize().clip(CircleShape).border(1.dp, p.hairline, CircleShape)) {
map(Modifier.fillMaxSize(), true)
}
}
}
Spacer(Modifier.height(4.dp))
Row(
Modifier
.clip(RoundedCornerShape(99.dp))
.background(p.panel.copy(alpha = 0.96f))
.border(1.dp, p.acc.copy(alpha = 0.55f), RoundedCornerShape(99.dp))
.padding(horizontal = 9.dp, vertical = 4.dp),
horizontalArrangement = Arrangement.spacedBy(8.dp),
verticalAlignment = Alignment.CenterVertically
) {
if (hasAmniMap && carSurface && useAmniMap) {
Text("⌨", color = p.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onOpenOrbKb() })
}
if (onVoice != null) {
Text("🎤", fontSize = 11.sp, modifier = Modifier.clickable { onVoice() })
}
if (hasAmniMap && carSurface) {
Text(
if (useAmniMap) "BUILT-IN" else "AMNI-MAP",
color = p.acc,
fontFamily = FontFamily.Monospace,
fontSize = 9.5.sp,
fontWeight = FontWeight.Bold,
modifier = Modifier.clickable { onToggleAmniMap() }
)
}
Text("N · $speedMph MPH", color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
Text(
"⤢ FULL",
color = p.acc,
fontFamily = FontFamily.Monospace,
fontSize = 9.5.sp,
fontWeight = FontWeight.Bold,
modifier = Modifier.clickable { onToggleExpand("MAP") }
)
}
}
DraggableDivider(totalPx, { d -> liveMapRatio = (liveMapRatio + d).coerceIn(0.42f, 0.70f) }, { onSetMapSplitRatio(liveMapRatio) }, { liveMapRatio = 0.56f; onSetMapSplitRatio(0.56f) })
media(Modifier.weight(mediaFrac).fillMaxHeight(), dense)
}
}
TileLayout.MAP_ONLY -> Box(Modifier.fillMaxSize().padding(pad)) {
map(Modifier.fillMaxSize(), false)
Row(
Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(6.dp).clip(RoundedCornerShape(14.dp))
.background(p.panel.copy(alpha = 0.95f)).border(1.dp, p.hairline, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
horizontalArrangement = Arrangement.spacedBy(8.dp),
verticalAlignment = Alignment.CenterVertically
) {
Text(weather?.let { "${it.emoji} ${it.tempF}°" } ?: "WX --", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
Text("$speedMph MPH", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)
MediaMiniBar(Modifier.weight(1f).clickable { onOpenMediaPicker() })
Text(if (cfg.masterGateEnabled && cfg.driverGateEnabled) "GATE" else "OPEN", color = if (cfg.masterGateEnabled && cfg.driverGateEnabled) p.acc else p.err, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}
}
TileLayout.STACKED -> Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.spacedBy(gap)) {
map(Modifier.weight(1.45f).fillMaxWidth(), false)
Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
media(Modifier.weight(1.1f).fillMaxHeight(), true)
widgets(Modifier.weight(0.9f).fillMaxHeight())
}
}
TileLayout.MAP_FOCUS -> BoxWithConstraints(Modifier.fillMaxSize().padding(pad)) {
val totalPx = with(dens) { maxWidth.toPx() }
val mapFrac = liveMapRatio.coerceIn(0.45f, 0.78f)
Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
map(Modifier.weight(mapFrac).fillMaxHeight(), false)
DraggableDivider(totalPx, { d -> liveMapRatio = (liveMapRatio + d).coerceIn(0.45f, 0.78f) }, { onSetMapSplitRatio(liveMapRatio) }, { liveMapRatio = 0.66f; onSetMapSplitRatio(0.66f) })
Column(Modifier.weight(1f - mapFrac).fillMaxHeight().padding(top = carTopPad), verticalArrangement = Arrangement.spacedBy(gap)) {
if (dense) {
media(Modifier.weight(1f).fillMaxWidth(), true)
Row(
Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.panel.copy(alpha = 0.94f)).border(1.dp, p.hairline, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
horizontalArrangement = Arrangement.SpaceBetween,
verticalAlignment = Alignment.CenterVertically
) {
Text(weather?.let { "${it.emoji}${it.tempF}°" } ?: "--°", color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
Text("$speedMph MPH", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
}
} else {
WeatherCard(weather, dense)
TripCard(speedMph, route, dense)
GateHintCard(cfg, dense)
media(Modifier.weight(1f).fillMaxWidth(), true)
}
}
}
}
TileLayout.MEDIA_FOCUS -> BoxWithConstraints(Modifier.fillMaxSize().padding(pad)) {
val totalPx = with(dens) { maxWidth.toPx() }
val wFrac = if (widgetW >= 130.dp) liveWidgetRatio.coerceIn(0.16f, 0.34f) else 0f
val mainRem = (1f - wFrac).coerceAtLeast(0.55f)
val firstFrac = (mainRem * (1f - liveMapRatio.coerceIn(0.32f, 0.72f))).coerceAtLeast(0.18f)
val secondFrac = (mainRem - firstFrac).coerceAtLeast(0.22f)
Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
if (!swapMapMedia) {
map(Modifier.weight(firstFrac).fillMaxHeight(), false)
DraggableDivider(totalPx, { d -> liveMapRatio = (liveMapRatio - d).coerceIn(0.32f, 0.72f) }, { onSetMapSplitRatio(liveMapRatio) }, { liveMapRatio = 0.58f; onSetMapSplitRatio(0.58f) })
media(Modifier.weight(secondFrac).fillMaxHeight(), dense)
} else {
media(Modifier.weight(secondFrac).fillMaxHeight(), dense)
DraggableDivider(totalPx, { d -> liveMapRatio = (liveMapRatio + d).coerceIn(0.32f, 0.72f) }, { onSetMapSplitRatio(liveMapRatio) }, { liveMapRatio = 0.58f; onSetMapSplitRatio(0.58f) })
map(Modifier.weight(firstFrac).fillMaxHeight(), false)
}
if (wFrac > 0f) {
DraggableDivider(totalPx, { d -> liveWidgetRatio = (liveWidgetRatio - d).coerceIn(0.16f, 0.34f) }, { onSetWidgetRatio(liveWidgetRatio) }, { liveWidgetRatio = 0.22f; onSetWidgetRatio(0.22f) })
widgets(Modifier.weight(wFrac).fillMaxHeight().padding(top = carTopPad))
}
}
}
TileLayout.BALANCED, TileLayout.AUTO -> BoxWithConstraints(Modifier.fillMaxSize().padding(pad)) {
val totalPx = with(dens) { maxWidth.toPx() }
val wFrac = liveWidgetRatio.coerceIn(0.16f, 0.36f)
val mainRem = (1f - wFrac).coerceAtLeast(0.55f)
val leftFrac = (mainRem * liveMapRatio.coerceIn(0.28f, 0.75f)).coerceAtLeast(0.20f)
val midFrac = (mainRem - leftFrac).coerceAtLeast(0.20f)
Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
if (!swapMapMedia) {
map(Modifier.weight(leftFrac).fillMaxHeight(), false)
DraggableDivider(totalPx, { d -> liveMapRatio = (liveMapRatio + d).coerceIn(0.28f, 0.75f) }, { onSetMapSplitRatio(liveMapRatio) }, { liveMapRatio = 0.56f; onSetMapSplitRatio(0.56f) })
media(Modifier.weight(midFrac).fillMaxHeight(), dense)
} else {
media(Modifier.weight(leftFrac).fillMaxHeight(), dense)
DraggableDivider(totalPx, { d -> liveMapRatio = (liveMapRatio + d).coerceIn(0.28f, 0.75f) }, { onSetMapSplitRatio(liveMapRatio) }, { liveMapRatio = 0.56f; onSetMapSplitRatio(0.56f) })
map(Modifier.weight(midFrac).fillMaxHeight(), false)
}
DraggableDivider(totalPx, { d -> liveWidgetRatio = (liveWidgetRatio - d).coerceIn(0.16f, 0.36f) }, { onSetWidgetRatio(liveWidgetRatio) }, { liveWidgetRatio = 0.24f; onSetWidgetRatio(0.24f) })
widgets(Modifier.weight(wFrac).fillMaxHeight().padding(top = carTopPad))
}
}
}
}
}
@Composable
private fun BottomCenterDock(
page: DrivePage,
gateOn: Boolean,
compact: Boolean,
onMenu: () -> Unit,
onPage: (DrivePage) -> Unit,
onMedia: () -> Unit,
onLayout: () -> Unit,
onWidgets: () -> Unit,
onToggleGate: () -> Unit
) {
val p = LocalAmni.current
Box(
Modifier.fillMaxWidth().padding(bottom = if (compact) 4.dp else 8.dp, top = 2.dp),
contentAlignment = Alignment.Center
) {
Row(
Modifier
.clip(RoundedCornerShape(99.dp))
.background(p.panel.copy(alpha = 0.96f))
.border(1.dp, p.hairline, RoundedCornerShape(99.dp))
.padding(horizontal = 8.dp, vertical = 4.dp),
horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
verticalAlignment = Alignment.CenterVertically
) {
DockPillBtn(Icons.Rounded.Menu, "MENU", false, false, onMenu)
DockPillBtn(Icons.Rounded.Home, "HOME", page == DrivePage.HOME, false) { onPage(DrivePage.HOME) }
DockPillBtn(Icons.Rounded.Apps, "APPS", page == DrivePage.APPS, false) { onPage(DrivePage.APPS) }
DockPillBtn(Icons.Rounded.Map, "NAV", page == DrivePage.NAV, false) { onPage(DrivePage.NAV) }
Box(Modifier.width(1.dp).height(20.dp).background(p.hairline))
DockPillBtn(Icons.Rounded.MusicNote, "MEDIA", false, false, onMedia)
DockPillBtn(Icons.Rounded.ViewModule, "LAYOUT", false, false, onLayout)
DockPillBtn(Icons.Rounded.Security, if (gateOn) "GATE" else "OPEN", gateOn, !gateOn, onToggleGate)
}
}
}
@Composable
private fun DockPillBtn(icon: ImageVector, label: String, sel: Boolean, err: Boolean, onClick: () -> Unit) {
val p = LocalAmni.current
val tint = if (err) p.err else if (sel) p.acc else p.ink
Row(
Modifier
.clip(RoundedCornerShape(99.dp))
.background(if (sel || err) tint.copy(alpha = 0.20f) else p.panel2.copy(alpha = 0.65f))
.border(1.dp, if (sel || err) tint.copy(alpha = 0.65f) else Color.Transparent, RoundedCornerShape(99.dp))
.clickable { onClick() }
.padding(horizontal = 10.dp, vertical = 6.dp),
verticalAlignment = Alignment.CenterVertically,
horizontalArrangement = Arrangement.spacedBy(4.dp)
) {
Icon(icon, label, Modifier.size(17.dp), tint = tint)
Text(label, color = tint, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}
}
@Composable
private fun TileLayoutPicker(
selected: TileLayout,
effective: TileLayout,
dockPosition: Int = 0,
swapMapMedia: Boolean = false,
carUiScale: Float = 1.0f,
fontScale: Float = 1.0f,
onPick: (TileLayout) -> Unit,
onSetDock: (Int) -> Unit = {},
onToggleSwap: () -> Unit = {},
onSetCarUiScale: (Float) -> Unit = {},
onSetFontScale: (Float) -> Unit = {},
onClose: () -> Unit,
compact: Boolean = false
) {
val p = LocalAmni.current
val rowH = if (compact) 42.dp else 52.dp
Box(Modifier.fillMaxSize().background(p.scrim).clickable { onClose() }, contentAlignment = Alignment.Center) {
SoftSurface(
Modifier
.fillMaxWidth(if (compact) 0.84f else 0.78f)
.padding(if (compact) 7.dp else 12.dp)
.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
radius = 20.dp
) {
Column(Modifier.padding(if (compact) 11.dp else 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
Row(verticalAlignment = Alignment.CenterVertically) {
Text("COCKPIT LAYOUT & UI SCALE", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = if (compact) 12.5.sp else 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
Spacer(Modifier.weight(1f))
Box(
Modifier.clip(RoundedCornerShape(99.dp)).background(p.panel2).border(1.dp, p.hairline, RoundedCornerShape(99.dp))
.clickable { onClose() }.padding(horizontal = 11.dp, vertical = 5.dp)
) {
Text("CLOSE ✕", color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
}
}
Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
Text("UI SIZE:", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
listOf("85%" to 0.85f, "100%" to 1.0f, "115%" to 1.15f, "130%" to 1.30f).forEach { (lbl, sc) ->
val on = kotlin.math.abs(carUiScale - sc) < 0.04f
Box(
Modifier.clip(RoundedCornerShape(8.dp))
.background(if (on) p.acc else p.panel2)
.border(1.dp, if (on) p.acc else p.hairline, RoundedCornerShape(8.dp))
.clickable { onSetCarUiScale(sc) }
.padding(horizontal = 9.dp, vertical = 5.dp)
) {
Text(lbl, color = if (on) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}
}
Spacer(Modifier.width(6.dp))
Text("TEXT:", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
listOf("STD" to 1.0f, "LG" to 1.15f, "XL" to 1.30f).forEach { (lbl, fs) ->
val on = kotlin.math.abs(fontScale - fs) < 0.04f
Box(
Modifier.clip(RoundedCornerShape(8.dp))
.background(if (on) p.acc else p.panel2)
.border(1.dp, if (on) p.acc else p.hairline, RoundedCornerShape(8.dp))
.clickable { onSetFontScale(fs) }
.padding(horizontal = 8.dp, vertical = 5.dp)
) {
Text(lbl, color = if (on) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}
}
}
Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
Text("CONTROLS:", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
listOf("LEFT RAIL" to 0, "BOTTOM DOCK" to 1).forEach { (lbl, pos) ->
val on = dockPosition == pos
Box(
Modifier.clip(RoundedCornerShape(8.dp))
.background(if (on) p.acc else p.panel2)
.border(1.dp, if (on) p.acc else p.hairline, RoundedCornerShape(8.dp))
.clickable { onSetDock(pos) }
.padding(horizontal = 9.dp, vertical = 5.dp)
) {
Text(lbl, color = if (on) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}
}
Spacer(Modifier.weight(1f))
Box(
Modifier.clip(RoundedCornerShape(8.dp))
.background(if (swapMapMedia) p.accDim else p.panel2)
.border(1.dp, if (swapMapMedia) p.acc else p.hairline, RoundedCornerShape(8.dp))
.clickable { onToggleSwap() }
.padding(horizontal = 9.dp, vertical = 5.dp)
) {
Text("⇄ SWAP MAP/MEDIA", color = if (swapMapMedia) p.acc else p.ink, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}
}
Text("Drag any divider bar to resize tiles · double-tap divider to reset · tap ⤢ on any tile for full screen", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp)
TileLayout.entries.forEach { mode ->
val sel = selected == mode
Row(
Modifier.fillMaxWidth().height(rowH).clip(RoundedCornerShape(if (compact) 10.dp else 14.dp))
.background(if (sel) p.accDim else p.panel2.copy(alpha = 0.55f))
.border(1.dp, if (sel) p.acc.copy(alpha = 0.7f) else p.hairline, RoundedCornerShape(if (compact) 10.dp else 14.dp))
.clickable { onPick(mode) }.padding(horizontal = if (compact) 9.dp else 12.dp),
verticalAlignment = Alignment.CenterVertically,
horizontalArrangement = Arrangement.spacedBy(if (compact) 9.dp else 12.dp)
) {
LayoutThumb(mode, sel)
Column(Modifier.weight(1f)) {
Text(mode.label.uppercase(), color = if (sel) p.acc else p.ink, fontFamily = FontFamily.Monospace, fontSize = if (compact) 11.5.sp else 12.sp, fontWeight = FontWeight.Bold)
Text(mode.blurb, color = p.soft, fontSize = if (compact) 10.sp else 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
if (sel) Text("●", color = p.acc, fontSize = 12.sp)
}
}
}
}
}
}
@Composable
private fun LayoutThumb(mode: TileLayout, sel: Boolean) {
val p = LocalAmni.current
val a = if (sel) p.acc else p.soft
Box(Modifier.size(44.dp, 30.dp).clip(RoundedCornerShape(7.dp)).background(p.panel2).border(1.dp, a.copy(alpha = 0.45f), RoundedCornerShape(7.dp)).padding(3.dp)) {
when (mode) {
TileLayout.SPLIT_50 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
Box(Modifier.weight(1f).fillMaxHeight().background(a.copy(alpha = 0.55f), RoundedCornerShape(2.dp)))
Box(Modifier.weight(1f).fillMaxHeight().background(a.copy(alpha = 0.35f), RoundedCornerShape(2.dp)))
}
TileLayout.ORB_CENTER -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
Box(Modifier.weight(0.65f).fillMaxHeight().background(a.copy(alpha = 0.3f), RoundedCornerShape(2.dp)))
Box(Modifier.size(18.dp).clip(CircleShape).background(a.copy(alpha = 0.6f)).border(1.dp, a, CircleShape))
Box(Modifier.weight(0.75f).fillMaxHeight().background(a.copy(alpha = 0.38f), RoundedCornerShape(2.dp)))
}
TileLayout.MAP_ONLY -> Box(Modifier.fillMaxSize().background(a.copy(alpha = 0.5f), RoundedCornerShape(3.dp)))
TileLayout.STACKED -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
Box(Modifier.weight(1.4f).fillMaxWidth().background(a.copy(alpha = 0.55f), RoundedCornerShape(2.dp)))
Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
Box(Modifier.weight(1f).fillMaxHeight().background(a.copy(alpha = 0.35f), RoundedCornerShape(2.dp)))
Box(Modifier.weight(1f).fillMaxHeight().background(a.copy(alpha = 0.3f), RoundedCornerShape(2.dp)))
}
}
TileLayout.MAP_FOCUS -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
Box(Modifier.weight(2.2f).fillMaxHeight().background(a.copy(alpha = 0.55f), RoundedCornerShape(2.dp)))
Box(Modifier.weight(0.7f).fillMaxHeight().background(a.copy(alpha = 0.32f), RoundedCornerShape(2.dp)))
}
TileLayout.MEDIA_FOCUS -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
Box(Modifier.weight(0.95f).fillMaxHeight().background(a.copy(alpha = 0.45f), RoundedCornerShape(2.dp)))
Box(Modifier.weight(1.4f).fillMaxHeight().background(a.copy(alpha = 0.55f), RoundedCornerShape(2.dp)))
Box(Modifier.weight(0.65f).fillMaxHeight().background(a.copy(alpha = 0.28f), RoundedCornerShape(2.dp)))
}
TileLayout.BALANCED, TileLayout.AUTO -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
Box(Modifier.weight(1.5f).fillMaxHeight().background(a.copy(alpha = 0.55f), RoundedCornerShape(2.dp)))
Box(Modifier.weight(1f).fillMaxHeight().background(a.copy(alpha = 0.38f), RoundedCornerShape(2.dp)))
Box(Modifier.weight(0.75f).fillMaxHeight().background(a.copy(alpha = 0.3f), RoundedCornerShape(2.dp)))
}
}
}
}
@Composable
private fun Rail(page: DrivePage, gateOn: Boolean, onMenu: () -> Unit, onPage: (DrivePage) -> Unit, onToggleGate: () -> Unit, railW: androidx.compose.ui.unit.Dp = 92.dp, compact: Boolean = false) {
val p = LocalAmni.current
Column(
Modifier.width(railW).fillMaxHeight()
.background(Brush.verticalGradient(listOf(p.panel.copy(alpha = 0.96f), p.bg.copy(alpha = 0.94f))))
.border(width = 1.dp, brush = Brush.horizontalGradient(listOf(Color.Transparent, p.hairline)), shape = RoundedCornerShape(0.dp))
.padding(vertical = if (compact) 4.dp else 14.dp, horizontal = if (compact) 2.dp else 8.dp),
horizontalAlignment = Alignment.CenterHorizontally,
verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 7.dp)
) {
if (!compact) {
Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 8.dp)) {
Box(Modifier.size(10.dp).clip(CircleShape).background(p.acc))
Spacer(Modifier.height(7.dp))
Text("AMNI", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
Text("DRIVE", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 3.sp)
}
} else {
Box(Modifier.size(if (railW < 58.dp) 7.dp else 8.dp).clip(CircleShape).background(p.acc))
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
val tint = if (err) p.err else if (sel) p.acc else p.soft
val scale by animateFloatAsState(if (sel) 1.04f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "rail")
val micro = compact && railW < 58.dp
val boxW = if (micro) 42.dp else if (compact) 48.dp else 76.dp
val boxH = if (micro) 40.dp else if (compact) 44.dp else 70.dp
val iconSz = if (micro) 20.dp else if (compact) 22.dp else 26.dp
Column(
Modifier.scale(scale).size(boxW, boxH).clip(RoundedCornerShape(if (compact) 12.dp else 18.dp))
.background(if (sel || err) Brush.verticalGradient(listOf(tint.copy(alpha = 0.22f), tint.copy(alpha = 0.08f))) else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)))
.border(1.dp, if (sel || err) tint.copy(alpha = 0.55f) else Color.Transparent, RoundedCornerShape(if (compact) 12.dp else 18.dp))
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
private fun CarStatusStrip(
state: DriveState,
speedMph: Int,
weather: WeatherNow?,
passenger: Boolean,
endPad: androidx.compose.ui.unit.Dp = 0.dp,
voiceActive: Boolean = false,
onVoice: () -> Unit = {},
onOpenLayout: () -> Unit = {},
onOpenMedia: () -> Unit = {},
onOpenWidgets: () -> Unit = {}
) {
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
Modifier.fillMaxWidth().height(37.dp).padding(start = 8.dp, end = 8.dp + endPad),
verticalAlignment = Alignment.CenterVertically,
horizontalArrangement = Arrangement.spacedBy(5.dp)
) {
Text("AMNI", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
Chip((if (passenger) "PSGR · " else "") + stTxt, if (moving) p.gold else p.acc)
weather?.let { Chip("${it.emoji} ${it.tempF}°", p.ink) }
Spacer(Modifier.weight(1f))
Box(
Modifier.clip(RoundedCornerShape(99.dp))
.background(if (voiceActive) p.acc else p.accDim)
.border(1.dp, p.acc.copy(alpha = 0.7f), RoundedCornerShape(99.dp))
.clickable { onVoice() }
.padding(horizontal = 10.dp, vertical = 4.dp)
) {
Text(
"🎤",
fontSize = 12.5.sp,
fontWeight = FontWeight.Bold
)
}
Box(Modifier.clip(RoundedCornerShape(99.dp)).background(p.panel2).border(1.dp, p.acc.copy(alpha = 0.5f), RoundedCornerShape(99.dp)).clickable { onOpenMedia() }.padding(horizontal = 10.dp, vertical = 4.dp)) {
Text("♪ MEDIA", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
}
Box(Modifier.clip(RoundedCornerShape(99.dp)).background(p.panel2).border(1.dp, p.hairline, RoundedCornerShape(99.dp)).clickable { onOpenLayout() }.padding(horizontal = 10.dp, vertical = 4.dp)) {
Text("⊞ LAYOUT · SIZE", color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
}
Box(Modifier.clip(RoundedCornerShape(99.dp)).background(p.panel2).border(1.dp, p.hairline, RoundedCornerShape(99.dp)).clickable { onOpenWidgets() }.padding(horizontal = 10.dp, vertical = 4.dp)) {
Text("✎ WIDGETS", color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
}
Text(clock, color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
}
}
@Composable
private fun MenuDrawer(
page: DrivePage,
rearAvailable: Boolean,
gateOn: Boolean,
weatherOn: Boolean,
carSurface: Boolean,
tileLayout: TileLayout,
dockPosition: Int = 0,
swapMapMedia: Boolean = false,
carUiScale: Float = 1.0f,
fontScale: Float = 1.0f,
onPage: (DrivePage) -> Unit,
onToggleGate: () -> Unit,
onToggleWeather: (Boolean) -> Unit,
onSettings: () -> Unit,
onClose: () -> Unit,
onMedia: () -> Unit,
onTileLayout: () -> Unit,
onWidgets: () -> Unit = {},
onMapStudio: () -> Unit = {},
onReportBug: () -> Unit = {},
onTutorial: () -> Unit = {},
onToggleDock: () -> Unit = {},
onToggleSwap: () -> Unit = {},
onSetCarUiScale: (Float) -> Unit = {},
onSetFontScale: (Float) -> Unit = {},
themeLabel: String = "",
onTheme: () -> Unit = {}
) {
val p = LocalAmni.current
val ctx = LocalContext.current
Row(Modifier.fillMaxSize().background(p.scrim)) {
SoftSurface(
Modifier
.width(347.dp)
.fillMaxHeight()
.padding(8.dp)
.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
radius = 22.dp
) {
Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
Box(Modifier.size(8.dp).clip(CircleShape).background(p.acc))
Text("AMNI DRIVE", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
Spacer(Modifier.weight(1f))
Box(
Modifier
.height(34.dp)
.clip(RoundedCornerShape(99.dp))
.background(p.panel2)
.border(1.dp, p.acc.copy(alpha = 0.65f), RoundedCornerShape(99.dp))
.clickable { onClose() }
.padding(horizontal = 12.dp),
contentAlignment = Alignment.Center
) {
Text("CLOSE ✕", color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
}
}
Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
Text("SIZE:", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
listOf("85%" to 0.85f, "100%" to 1.0f, "115%" to 1.15f, "130%" to 1.30f).forEach { (lbl, sc) ->
val on = kotlin.math.abs(carUiScale - sc) < 0.04f
Box(Modifier.clip(RoundedCornerShape(7.dp)).background(if (on) p.acc else p.panel2).border(1.dp, if (on) p.acc else p.hairline, RoundedCornerShape(7.dp)).clickable { onSetCarUiScale(sc) }.padding(horizontal = 8.dp, vertical = 4.dp)) {
Text(lbl, color = if (on) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}
}
}
Row(Modifier.fillMaxWidth().padding(bottom = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
Text("TEXT:", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
listOf("NORMAL" to 1.0f, "LARGE" to 1.15f, "XL" to 1.30f).forEach { (lbl, fs) ->
val on = kotlin.math.abs(fontScale - fs) < 0.04f
Box(Modifier.clip(RoundedCornerShape(7.dp)).background(if (on) p.acc else p.panel2).border(1.dp, if (on) p.acc else p.hairline, RoundedCornerShape(7.dp)).clickable { onSetFontScale(fs) }.padding(horizontal = 8.dp, vertical = 4.dp)) {
Text(lbl, color = if (on) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}
}
}
if (!carSurface) {
MenuRow(Icons.Rounded.Home, "Home", "Dashboard & map", page == DrivePage.HOME) { onPage(DrivePage.HOME) }
MenuRow(Icons.Rounded.Apps, "All Apps", "Everything installed", page == DrivePage.APPS) { onPage(DrivePage.APPS) }
MenuRow(Icons.Rounded.Map, "Navigation", "Full-screen map", page == DrivePage.NAV) { onPage(DrivePage.NAV) }
}
MenuRow(Icons.Rounded.MusicNote, "Media & Apps", "Signed-in Spotify, YT Music, web & apps", false) { onMedia() }
MenuRow(Icons.Rounded.Map, "Amni-Map 3D Studio", "3D buildings · trees · bridges · vehicles", false) { onMapStudio() }
MenuRow(Icons.Rounded.Palette, "Theme", "$themeLabel · tap to cycle", false) { onTheme() }
MenuRow(Icons.Rounded.ViewModule, "Tile layout", tileLayout.label + " · fit glass", false) { onTileLayout() }
MenuRow(Icons.Rounded.Apps, "Widgets", "Customize & drag-edit cards", false) { onWidgets() }
MenuRow(Icons.Rounded.ViewModule, "Controls dock", if (dockPosition == 1) "Bottom center · tap for left rail" else "Left rail · tap for bottom center", dockPosition == 1) { onToggleDock() }
MenuRow(Icons.Rounded.ViewModule, "Swap Map ↔ Media", if (swapMapMedia) "Swapped · tap to restore" else "Standard · tap to flip sides", swapMapMedia) { onToggleSwap() }
if (!carSurface) {
MenuRow(Icons.Rounded.Phone, "Phone", "Dialer", false) {
runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
onClose()
}
MenuRow(Icons.Rounded.Tv, "Rear Screen", if (rearAvailable) "Detected — auto-routes gated apps" else "No rear display detected", false) {}
MenuRow(Icons.Rounded.Cast, "Amni Link", "Phone host · head-unit receiver", false) {
runCatching { ctx.startActivity(Intent(ctx, LinkActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
onClose()
}
}
Box(Modifier.fillMaxWidth().height(1.dp).background(p.hairline).padding(vertical = 5.dp))
MenuSwitch(Icons.Rounded.Security, "Driver gate", gateOn) { onToggleGate() }
MenuSwitch(Icons.Rounded.WaterDrop, "Weather background", weatherOn) { onToggleWeather(it) }
Box(Modifier.fillMaxWidth().height(1.dp).background(p.hairline))
MenuRow(Icons.Rounded.Info, "Report Bug", "Attach scrubbed log · no personal data", false) { onReportBug() }
if (!carSurface) {
MenuRow(Icons.Rounded.Info, "Setup & Connection Guide", "First-launch tutorial · audio · USB/Wi-Fi", false) { onTutorial() }
MenuRow(Icons.Rounded.Settings, "Settings", "Gate · themes · 3D map · wire · link", false, onSettings)
}
Spacer(Modifier.weight(1f))
Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
Icon(Icons.Rounded.Info, null, Modifier.size(14.dp), tint = p.dim)
Text("v${com.amni.drive.BuildConfig.VERSION_NAME} · Amni-Scient · AA · Link", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 8.sp)
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
private fun MediaCard(
modifier: Modifier,
dense: Boolean = false,
preferred: String? = null,
surfaceDisplayId: Int? = null,
mediaApps: List<AppEntry> = emptyList(),
onOpenPicker: () -> Unit = {},
isExpanded: Boolean = false,
onToggleExpand: (() -> Unit)? = null
) {
val p = LocalAmni.current
val ctx = LocalContext.current
val np by MediaSessionBridge.nowPlaying.collectAsState()
val audioArmed by AaAudioStreamer.armed.collectAsState()
val audioStreaming by AaAudioStreamer.streaming.collectAsState()
LaunchedEffect(Unit) { MediaSessionBridge.ensureStarted(ctx) }
val (src, fallbackPlaying) = rememberMediaSource(ctx)
val playing = np?.playing ?: fallbackPlaying
val title = np?.title?.takeIf { it.isNotBlank() } ?: preferred?.takeIf { it.isNotBlank() } ?: src
val subtitle = np?.let { listOf(it.artist, it.appLabel).filter { s -> s.isNotBlank() }.joinToString(" · ") } ?: ""
val hasAccess = remember(np) { MediaSessionBridge.hasAccess(ctx) }
val pad = if (dense) 8.dp else 16.dp
Box(modifier) {
GlassCard(Modifier.fillMaxSize()) {
Column(Modifier.fillMaxSize().padding(pad), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
AccentBar(Modifier.width(if (dense) 14.dp else 28.dp).height(3.dp))
Text("MEDIA", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = if (dense) 9.sp else 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
Spacer(Modifier.weight(1f))
Box(Modifier.clip(RoundedCornerShape(99.dp)).background(if (audioArmed) p.accDim else p.panel2).border(1.dp, if (audioArmed) p.acc else p.hairline, RoundedCornerShape(99.dp)).clickable { if (audioArmed) AaAudioStreamer.stopAll(ctx) else AudioCaptureGrantActivity.launch(ctx) }.padding(horizontal = 7.dp, vertical = 2.dp)) {
Text(if (audioStreaming) "🔊 LIVE" else if (audioArmed) "🔊 CAR" else "🔊 BT", color = if (audioArmed) p.acc else p.soft, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
}
Box(Modifier.clip(RoundedCornerShape(99.dp)).background(p.accDim).border(1.dp, p.acc.copy(alpha = 0.5f), RoundedCornerShape(99.dp)).clickable { onOpenPicker() }.padding(horizontal = 8.dp, vertical = 2.dp)) {
Text("SOURCE", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
}
if (onToggleExpand != null) {
Box(
Modifier.clip(RoundedCornerShape(99.dp))
.background(if (isExpanded) p.acc else p.panel2)
.border(1.dp, if (isExpanded) p.acc else p.hairline, RoundedCornerShape(99.dp))
.clickable { onToggleExpand() }
.padding(horizontal = 7.dp, vertical = 2.dp)
) {
Text(
if (isExpanded) "⤡" else "⤢",
color = if (isExpanded) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink,
fontFamily = FontFamily.Monospace,
fontSize = 9.sp,
fontWeight = FontWeight.Bold
)
}
}
}
Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onOpenPicker() }) {
Box(
Modifier.size(if (dense) 42.dp else 84.dp).clip(RoundedCornerShape(if (dense) 12.dp else 22.dp)).background(p.accDim).border(1.dp, p.acc.copy(alpha = 0.35f), RoundedCornerShape(if (dense) 12.dp else 22.dp)),
contentAlignment = Alignment.Center
) {
val art = np?.art
if (art != null) Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
else Icon(Icons.Rounded.MusicNote, null, Modifier.size(if (dense) 22.dp else 40.dp), tint = p.acc)
}
Spacer(Modifier.height(if (dense) 4.dp else 10.dp))
Text(title, color = p.ink, fontSize = if (dense) 13.sp else 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
Text(
when {
subtitle.isNotBlank() -> subtitle
!hasAccess -> "Tap SOURCE to pick music app"
playing -> "Playing · tap to switch"
mediaApps.isNotEmpty() -> "Tap SOURCE for Spotify / music"
else -> "Tap ▶ or SOURCE"
},
color = p.soft, fontSize = if (dense) 10.sp else 12.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis
)
}
if (!hasAccess && surfaceDisplayId == null && !dense) {
Box(Modifier.clip(RoundedCornerShape(99.dp)).background(p.accDim).border(1.dp, p.acc.copy(alpha = 0.45f), RoundedCornerShape(99.dp)).clickable { MediaSessionBridge.requestAccess(ctx) }.padding(horizontal = 10.dp, vertical = 5.dp)) {
Text("ENABLE MEDIA ACCESS", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold)
}
}
Row(horizontalArrangement = Arrangement.spacedBy(if (dense) 8.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
MediaCtrl(Icons.Rounded.SkipPrevious, "Prev", dense) { if (!MediaSessionBridge.prev()) mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
Box(
Modifier.size(if (dense) 44.dp else 56.dp).clip(CircleShape).background(p.accentBrush)
.clickable { if (!MediaSessionBridge.playPause()) mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) },
contentAlignment = Alignment.Center
) {
Text(if (playing) "❚❚" else "▶", color = if (p.isLight) Color.White else Color(0xFF05070A), fontSize = if (dense) 15.sp else 18.sp, fontWeight = FontWeight.Bold)
}
MediaCtrl(Icons.Rounded.SkipNext, "Next", dense) { if (!MediaSessionBridge.next()) mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_NEXT) }
}
if (!dense) {
Box(
Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, p.acc.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
.clickable { if (!MediaSessionBridge.play()) mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PLAY) }
.padding(horizontal = 18.dp, vertical = 10.dp)
) {
Text("PLAY / RESUME", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
}
}
}
}
}
}
@Composable
private fun MediaSourceSheet(
mediaApps: List<AppEntry>,
allApps: List<AppEntry>,
onClose: () -> Unit,
onPick: (AppEntry) -> Unit,
onPickWeb: (WebAppEntry) -> Unit = {}
) {
val p = LocalAmni.current
val ctx = LocalContext.current
var tab by remember { mutableStateOf(if (mediaApps.isNotEmpty()) 0 else 1) }
var query by remember { mutableStateOf("") }
var showKb by remember { mutableStateOf(false) }
var voiceStatus by remember { mutableStateOf<String?>(null) }
val startVoiceFilter: () -> Unit = {
VoiceSearch.listen(ctx, { voiceStatus = it }, { spoken ->
if (!spoken.isNullOrBlank()) { query = spoken.trim(); voiceStatus = null; showKb = false }
})
}
val dockedKb = BindDashKeyboard(
enabled = showKb,
spec = DashKeyboardSpec(
id = "media_source_filter",
preview = query,
voiceStatus = voiceStatus,
placeholder = "Filter Spotify, audio & web apps…",
enterLabel = "DONE ↵",
onText = { query = (query + it).take(32) },
onBackspace = { query = query.dropLast(1) },
onClear = { query = "" },
onEnter = { showKb = false },
onVoice = startVoiceFilter,
onClose = { showKb = false }
)
)
Box(Modifier.fillMaxSize().background(p.scrim).clickable { onClose() }, contentAlignment = Alignment.Center) {
SoftSurface(
Modifier
.fillMaxWidth(0.84f)
.padding(9.dp)
.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
radius = 18.dp
) {
Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
Text("MEDIA BOX", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
listOf("SIGNED-IN AUDIO", "CAR WEB APPS", "ALL (${allApps.size})").forEachIndexed { idx, label ->
val on = tab == idx
Box(Modifier.clip(RoundedCornerShape(99.dp)).background(if (on) p.acc else p.panel2).border(1.dp, p.acc.copy(alpha = 0.6f), RoundedCornerShape(99.dp)).clickable { tab = idx }.padding(horizontal = 9.dp, vertical = 4.dp)) {
Text(label, color = if (on) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}
}
Box(
Modifier.clip(RoundedCornerShape(99.dp))
.background(if (showKb || query.isNotBlank()) p.acc else p.panel2)
.border(1.dp, p.acc.copy(alpha = 0.65f), RoundedCornerShape(99.dp))
.clickable { showKb = !showKb }
.padding(horizontal = 9.dp, vertical = 4.dp)
) {
Text(if (query.isBlank()) "🔎 FILTER" else "🔎 ${query.take(10)}", color = if (showKb || query.isNotBlank()) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.acc, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}
Spacer(Modifier.weight(1f))
Box(
Modifier.clip(RoundedCornerShape(99.dp)).background(p.panel2).border(1.dp, p.hairline, RoundedCornerShape(99.dp))
.clickable { onClose() }.padding(horizontal = 10.dp, vertical = 4.dp)
) {
Text("CLOSE ✕", color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}
}
if (showKb && !dockedKb) {
CarTouchKeyboard(
preview = query,
voiceStatus = voiceStatus,
placeholder = "Filter Spotify, audio & web apps…",
enterLabel = "DONE ↵",
onText = { query = (query + it).take(32) },
onBackspace = { query = query.dropLast(1) },
onClear = { query = "" },
onEnter = { showKb = false },
onVoice = startVoiceFilter,
onClose = { showKb = false }
)
}
Row(
Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(12.dp)).background(p.accDim).border(1.dp, p.acc.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
.clickable {
if (!MediaSessionBridge.playPause()) mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
onClose()
}.padding(horizontal = 12.dp),
verticalAlignment = Alignment.CenterVertically
) {
Text("▶", color = p.acc, fontSize = 14.sp, fontWeight = FontWeight.Bold)
Spacer(Modifier.width(10.dp))
Text("Play / Resume Active Audio", color = p.ink, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
}
if (tab == 1) {
Text("Runs directly inside car display · synced with phone Web Sign-In", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp)
val webFiltered = if (query.isBlank()) WebAppEntry.CATALOG else WebAppEntry.CATALOG.filter { it.label.contains(query.trim(), ignoreCase = true) || it.id.contains(query.trim(), ignoreCase = true) }
webFiltered.chunked(2).forEach { pair ->
Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
pair.forEach { web ->
Row(
Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(12.dp)).background(p.panel2).border(1.dp, p.hairline, RoundedCornerShape(12.dp))
.clickable { onPickWeb(web) }.padding(horizontal = 10.dp),
verticalAlignment = Alignment.CenterVertically
) {
Text(web.emoji, fontSize = 16.sp)
Spacer(Modifier.width(8.dp))
Text(web.label, color = p.ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
}
}
}
}
} else {
Text("Uses your phone's active sign-in, playlists & audio session directly in the dashboard", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp)
val baseShown = if (tab == 2) allApps else mediaApps
val shown = if (query.isBlank()) baseShown else baseShown.filter { it.label.contains(query.trim(), ignoreCase = true) || it.packageName.contains(query.trim(), ignoreCase = true) }
if (shown.isEmpty()) Text("No matching apps · tap ALL or CAR WEB APPS above", color = p.soft, fontSize = 11.5.sp)
shown.take(50).forEach { app ->
val signedAudio = MediaBrowserHub.isAudioApp(ctx, app.packageName)
Row(
Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp)).background(p.panel2).border(1.dp, p.hairline, RoundedCornerShape(12.dp))
.clickable { onPick(app) }.padding(horizontal = 12.dp),
verticalAlignment = Alignment.CenterVertically
) {
Icon(Icons.Rounded.MusicNote, null, Modifier.size(18.dp), tint = p.acc)
Spacer(Modifier.width(10.dp))
Text(app.label, color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
Text(if (signedAudio) "✓ SIGNED IN →" else "OPEN →", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}
}
}
}
}
}
}
@Composable
private fun ClockWidgetCard(dense: Boolean) {
val p = LocalAmni.current
val pad = if (dense) 8.dp else 14.dp
val now by produceState(LocalTime.now()) {
while (true) { value = LocalTime.now(); delay(10000) }
}
val timeStr = now.format(DateTimeFormatter.ofPattern("h:mm"))
val amPm = now.format(DateTimeFormatter.ofPattern("a"))
GlassCard(Modifier.fillMaxWidth()) {
Column(Modifier.padding(pad)) {
Row(verticalAlignment = Alignment.CenterVertically) {
AccentBar(Modifier.width(if (dense) 14.dp else 24.dp).height(3.dp))
Spacer(Modifier.width(7.dp))
Text("CLOCK", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = if (dense) 10.sp else 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}
Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
Text(timeStr, color = p.ink, fontFamily = FontFamily.Monospace, fontSize = if (dense) 26.sp else 34.sp, fontWeight = FontWeight.Bold)
Text(amPm, color = p.acc, fontFamily = FontFamily.Monospace, fontSize = if (dense) 11.sp else 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 3.dp))
}
}
}
}
@Composable
private fun QuickMediaWidgetCard(dense: Boolean, onOpenPicker: () -> Unit) {
val p = LocalAmni.current
val ctx = LocalContext.current
val np by MediaSessionBridge.nowPlaying.collectAsState()
val (src, fbPlaying) = rememberMediaSource(ctx)
val playing = np?.playing ?: fbPlaying
val title = np?.title?.takeIf { it.isNotBlank() } ?: src
GlassCard(Modifier.fillMaxWidth()) {
Column(Modifier.padding(if (dense) 8.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
Row(verticalAlignment = Alignment.CenterVertically) {
AccentBar(Modifier.width(14.dp).height(3.dp))
Spacer(Modifier.width(7.dp))
Text(title, color = p.ink, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).clickable { onOpenPicker() })
}
Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
MediaCtrl(Icons.Rounded.SkipPrevious, "Prev", true) { if (!MediaSessionBridge.prev()) mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
Box(Modifier.size(34.dp).clip(CircleShape).background(p.accentBrush).clickable { if (!MediaSessionBridge.playPause()) mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }, contentAlignment = Alignment.Center) {
Text(if (playing) "❚❚" else "▶", color = if (p.isLight) Color.White else Color(0xFF05070A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
}
MediaCtrl(Icons.Rounded.SkipNext, "Next", true) { if (!MediaSessionBridge.next()) mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_NEXT) }
}
}
}
}
@Composable
private fun ShortcutsWidgetCard(allApps: List<AppEntry>, dense: Boolean, onQuickLaunch: (AppEntry) -> Unit) {
val p = LocalAmni.current
val picks = remember(allApps) {
val preferred = listOf("spotify", "youtube", "netflix", "amniscient.map", "music", "maps")
val found = preferred.mapNotNull { key -> allApps.firstOrNull { it.packageName.contains(key, true) } }.distinctBy { it.packageName }
(found + allApps).distinctBy { it.packageName }.take(4)
}
GlassCard(Modifier.fillMaxWidth()) {
Column(Modifier.padding(if (dense) 8.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
Row(verticalAlignment = Alignment.CenterVertically) {
AccentBar(Modifier.width(14.dp).height(3.dp))
Spacer(Modifier.width(7.dp))
Text("QUICK APPS", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}
picks.chunked(2).forEach { pair ->
Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
pair.forEach { app ->
Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(p.panel2).border(1.dp, p.hairline, RoundedCornerShape(8.dp)).clickable { onQuickLaunch(app) }.padding(horizontal = 7.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
Text(app.label, color = p.ink, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
}
}
}
}
}
}
@Composable
private fun VehicleWidgetCard(speedMph: Int, gpsOk: Boolean, netOk: Boolean, dense: Boolean) {
val p = LocalAmni.current
val mirrorLive by com.amni.drive.launcher.CarMirror.live.collectAsState()
val directReady by com.amni.drive.launcher.CarApps.ready.collectAsState()
GlassCard(Modifier.fillMaxWidth()) {
Column(Modifier.padding(if (dense) 8.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
Row(verticalAlignment = Alignment.CenterVertically) {
AccentBar(Modifier.width(14.dp).height(3.dp))
Spacer(Modifier.width(7.dp))
Text("VEHICLE", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}
Text("GPS ${if (gpsOk) "LOCK" else "WAIT"} · NET ${if (netOk) "OK" else "OFF"}", color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
Text("APPS ${if (directReady) "DIRECT" else if (mirrorLive) "MIRROR" else "WEB"} · $speedMph MPH", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
}
}
}
@Composable
private fun CompassWidgetCard(location: Location?, dense: Boolean) {
val p = LocalAmni.current
val deg = ((location?.bearing ?: 0f).roundToInt() % 360 + 360) % 360
val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
val cardinal = if (location != null && location.hasBearing()) dirs[((deg + 22) / 45) % 8] else "--"
GlassCard(Modifier.fillMaxWidth()) {
Column(Modifier.padding(if (dense) 8.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
Row(verticalAlignment = Alignment.CenterVertically) {
AccentBar(Modifier.width(14.dp).height(3.dp))
Spacer(Modifier.width(7.dp))
Text("COMPASS", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}
Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
Text(cardinal, color = p.acc, fontFamily = FontFamily.Monospace, fontSize = if (dense) 22.sp else 28.sp, fontWeight = FontWeight.Bold)
Text(if (location != null && location.hasBearing()) "${deg}°" else "GPS…", color = p.ink, fontFamily = FontFamily.Monospace, fontSize = if (dense) 12.sp else 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 2.dp))
}
}
}
}
@Composable
private fun MediaCtrl(icon: ImageVector, label: String, dense: Boolean, onClick: () -> Unit) {
val p = LocalAmni.current
Box(
Modifier.size(if (dense) 37.dp else 48.dp).clip(CircleShape).background(p.panel2).border(1.dp, p.hairline, CircleShape).clickable { onClick() },
contentAlignment = Alignment.Center
) {
Icon(icon, label, Modifier.size(if (dense) 18.dp else 24.dp), tint = p.ink)
}
}
@Composable
private fun MediaMiniBar(modifier: Modifier) {
val p = LocalAmni.current
val ctx = LocalContext.current
val (src, playing) = rememberMediaSource(ctx)
Row(modifier.clip(RoundedCornerShape(10.dp)).background(p.panel2).border(1.dp, p.hairline, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
Text(if (playing) "❚❚" else "▶", color = p.acc, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { if (!MediaSessionBridge.playPause()) mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) })
Text(src, color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
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
Spacer(Modifier.width(7.dp))
Text("WEATHER", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = if (dense) 10.sp else 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}
Row(Modifier.padding(top = if (dense) 4.dp else 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (dense) 7.dp else 12.dp)) {
Text(w?.emoji ?: "🌡️", fontSize = if (dense) 23.sp else 34.sp)
Text("${w?.tempF ?: "--"}°", color = p.ink, fontFamily = FontFamily.Monospace, fontSize = if (dense) 24.sp else 32.sp, fontWeight = FontWeight.Bold)
if (!dense) Column {
Text(w?.label ?: "Waiting for GPS…", color = p.ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
Text("WIND ${w?.windMph ?: "--"} · HUM ${w?.humidity ?: "--"}%", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp)
} else Text(w?.label ?: "GPS…", color = p.soft, fontSize = 11.5.sp, maxLines = 1, modifier = Modifier.weight(1f))
}
}
}
}
@Composable
private fun MsgBanner(m: IncomingMsg, onRead: () -> Unit, onDismiss: () -> Unit, dense: Boolean, modifier: Modifier = Modifier) {
val p = LocalAmni.current
GlassCard(modifier) {
Row(Modifier.padding(horizontal = if (dense) 10.dp else 14.dp, vertical = if (dense) 7.dp else 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (dense) 8.dp else 12.dp)) {
Text("💬", fontSize = if (dense) 15.sp else 18.sp)
Column {
Text(m.sender, color = p.ink, fontSize = if (dense) 13.sp else 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
Text(m.appLabel, color = p.dim, fontFamily = FontFamily.Monospace, fontSize = if (dense) 10.sp else 10.5.sp)
}
Box(Modifier.clip(RoundedCornerShape(99.dp)).background(p.accDim).border(1.dp, p.acc.copy(alpha = 0.4f), RoundedCornerShape(99.dp)).clickable { onRead() }.padding(horizontal = 10.dp, vertical = 5.dp)) {
Text("🔊 READ", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = if (dense) 10.5.sp else 11.sp, fontWeight = FontWeight.Bold)
}
Text("✕", color = p.dim, fontSize = if (dense) 13.sp else 14.sp, modifier = Modifier.clickable { onDismiss() })
}
}
}
@Composable
private fun TurnBanner(cue: TurnGuidance.Cue, next: RouteStep?, voiceOn: Boolean, onToggleVoice: () -> Unit, dense: Boolean, modifier: Modifier = Modifier) {
val p = LocalAmni.current
GlassCard(modifier) {
Row(Modifier.padding(horizontal = if (dense) 10.dp else 16.dp, vertical = if (dense) 7.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) {
Text(cue.arrow, color = p.acc, fontFamily = FontFamily.Monospace, fontSize = if (dense) 24.sp else 30.sp, fontWeight = FontWeight.Bold)
Spacer(Modifier.width(if (dense) 8.dp else 12.dp))
Column {
Text(TurnGuidance.distText(cue.distM), color = p.acc, fontFamily = FontFamily.Monospace, fontSize = if (dense) 15.sp else 18.sp, fontWeight = FontWeight.Bold)
Text(TurnGuidance.phrase(cue.step.type, cue.step.modifier, cue.step.name), color = p.soft, fontFamily = FontFamily.Monospace, fontSize = if (dense) 11.5.sp else 13.sp, maxLines = 1)
}
if (next != null && cue.distM < 400) {
Spacer(Modifier.width(if (dense) 8.dp else 12.dp))
Box(Modifier.clip(RoundedCornerShape(99.dp)).background(p.accDim).padding(horizontal = 8.dp, vertical = 3.dp)) {
Text("then ${TurnGuidance.arrowFor(next.type, next.modifier)}", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = if (dense) 11.sp else 12.sp, fontWeight = FontWeight.Bold)
}
}
Spacer(Modifier.width(if (dense) 10.dp else 16.dp))
Text(if (voiceOn) "🔊" else "🔇", fontSize = if (dense) 15.sp else 18.sp, modifier = Modifier.clickable { onToggleVoice() })
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
Spacer(Modifier.width(7.dp))
Text("TRIP", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = if (dense) 10.sp else 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}
Row(Modifier.padding(top = if (dense) 4.dp else 8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
Text(if (route != null) "${(route.durationS / 60).roundToInt()}m" else "$speedMph", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = if (dense) 24.sp else 30.sp, fontWeight = FontWeight.Bold)
Text(if (route != null) "%.1f mi · ${LocalTime.now().plusSeconds(route.durationS.toLong()).format(DateTimeFormatter.ofPattern("h:mm a"))}".format(route.distanceM / 1609.34) else "MPH", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = if (dense) 11.5.sp else 12.5.sp, modifier = Modifier.padding(bottom = 3.dp))
}
if (!dense) Text(if (route != null) "Search or long-press to add stops" else "Search or long-press the map to go", color = p.dim, fontSize = 11.5.sp, modifier = Modifier.padding(top = 4.dp))
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
Spacer(Modifier.width(7.dp))
Text("GATE", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = if (dense) 10.sp else 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
Spacer(Modifier.weight(1f))
Chip(if (on) "ON" else "OFF", if (on) p.acc else p.err)
}
if (!dense) Text(
if (on) "Video & games pause up front while moving. Rear never gates."
else "Everything unlocked this drive. Re-arms next start.",
color = p.soft, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp)
) else Text(if (on) "Moving locks video" else "Unlocked", color = p.soft, fontSize = 11.5.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
}
}
}
@Composable
private fun GateSheet(app: AppEntry, rear: Boolean, onRear: () -> Unit, onPassenger: () -> Unit, onDismiss: () -> Unit) {
val p = LocalAmni.current
Box(Modifier.fillMaxSize().background(p.scrim).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
GlassCard(
Modifier
.fillMaxWidth(0.82f)
.padding(10.dp)
.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
) {
Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
Box(Modifier.size(48.dp).clip(CircleShape).background(p.err.copy(alpha = 0.12f)).border(1.dp, p.err.copy(alpha = 0.45f), CircleShape), contentAlignment = Alignment.Center) {
Icon(Icons.Rounded.Lock, null, Modifier.size(24.dp), tint = p.err)
}
Text("${app.label} is gated while moving", color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
Text("Video pauses on the driver screen when moving. Send it rear — crew watches, you drive.", color = p.soft, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
if (rear) Box(Modifier.clip(RoundedCornerShape(12.dp)).background(p.accentBrush).clickable { onRear() }.padding(horizontal = 15.dp, vertical = 11.dp)) {
Text("SEND TO REAR →", color = if (p.isLight) Color.White else Color(0xFF05070A), fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
}
Box(Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, p.acc, RoundedCornerShape(12.dp)).clickable { onPassenger() }.padding(horizontal = 15.dp, vertical = 11.dp)) {
Text("I'M A PASSENGER", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
}
Box(Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, p.line2, RoundedCornerShape(12.dp)).clickable { onDismiss() }.padding(horizontal = 15.dp, vertical = 11.dp)) {
Text("CANCEL", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
}
}
Text("No hard locks · gate off = one tap in the dock", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, letterSpacing = 0.5.sp)
}
}
}
}
