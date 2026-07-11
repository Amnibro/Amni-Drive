package com.amni.drive.ui
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.ConnectivityManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amni.drive.LinkActivity
import com.amni.drive.config.DriveConfig
import com.amni.drive.gate.DriveState
import com.amni.drive.gate.GateDecision
import com.amni.drive.launcher.AppEntry
import com.amni.drive.map.OsrmRouting
import com.amni.drive.map.Place
import com.amni.drive.map.ReportStore
import com.amni.drive.map.RoutePoint
import com.amni.drive.map.RouteResult
import com.amni.drive.map.TileStore
import com.amni.drive.map.rememberMapCamera
import com.amni.drive.weather.WeatherNow
import com.amni.drive.weather.Wx
import kotlinx.coroutines.delay
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
onOpenSettings: () -> Unit
) {
val p = rememberPalette(theme)
CompositionLocalProvider(LocalAmni provides p) {
var page by remember { mutableStateOf(DrivePage.HOME) }
var menuOpen by remember { mutableStateOf(false) }
var blocked by remember { mutableStateOf<AppEntry?>(null) }
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
val onGo: (Place) -> Unit = { stops.clear(); stops.add(RoutePoint(it.lat, it.lon)) }
val onAddStop: (Place) -> Unit = { if (stops.isEmpty()) stops.add(RoutePoint(it.lat, it.lon)) else stops.add(stops.size - 1, RoutePoint(it.lat, it.lon)) }
val onEnd: () -> Unit = { stops.clear(); route = null }
val stopsKey = stops.joinToString("|") { "${it.lat},${it.lon}" }
LaunchedEffect(stopsKey, location == null) {
if (stops.isEmpty()) { route = null; return@LaunchedEffect }
val from = location ?: return@LaunchedEffect
routing = true
route = OsrmRouting.route(listOf(RoutePoint(from.latitude, from.longitude)) + stops.toList())
routing = false
}
val ctx = LocalContext.current
val netOk by produceState(true) {
val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
while (true) { value = cm.activeNetwork != null; delay(3000) }
}
Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(p.bg, if (p.isLight) Color(0xFFE8ECF0) else Color(0xFF05070A))))) {
if (theme.weatherBg) WeatherLayer(weather?.wx ?: Wx.NONE, moving, Modifier.fillMaxSize())
Row(Modifier.fillMaxSize()) {
Rail(page, cfg.driverGateEnabled && cfg.masterGateEnabled, { menuOpen = true }, { page = it }, onToggleGate)
Column(Modifier.weight(1f).fillMaxHeight()) {
TopBar(driveState, speedMph, weather, passenger)
AnimatedContent(
targetState = page,
transitionSpec = {
(fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + slideInHorizontally { it / 24 }) togetherWith
(fadeOut() + slideOutHorizontally { -it / 24 })
},
modifier = Modifier.weight(1f).fillMaxWidth(),
label = "page"
) { pg ->
when (pg) {
DrivePage.APPS -> AppDrawer(apps, cfg, driveState, passenger, theme.tileScale) { app -> if (!onLaunch(app).allow) blocked = app }
DrivePage.NAV -> Box(Modifier.fillMaxSize().padding(12.dp, 4.dp, 12.dp, 12.dp)) {
MapCard(Modifier.fillMaxSize(), camera, tiles, route, reports, stops, location, routing, true, netOk, showLayers, showReport, { showLayers = it }, { showReport = it }, reportStore, addPoint, onGo, onAddStop, onEnd)
}
DrivePage.HOME -> Row(Modifier.fillMaxSize().padding(12.dp, 4.dp, 12.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
MapCard(Modifier.weight(1.55f).fillMaxHeight(), camera, tiles, route, reports, stops, location, routing, false, netOk, showLayers, showReport, { showLayers = it }, { showReport = it }, reportStore, addPoint, onGo, onAddStop, onEnd)
MediaCard(Modifier.weight(1f).fillMaxHeight())
Column(Modifier.width(260.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
WeatherCard(weather)
TripCard(speedMph, route)
GateHintCard(cfg)
}
}
}
}
}
}
if (menuOpen) MenuDrawer(page, rearAvailable, cfg.driverGateEnabled && cfg.masterGateEnabled, theme.weatherBg,
onPage = { page = it; menuOpen = false }, onToggleGate = onToggleGate, onToggleWeather = onToggleWeather,
onSettings = { menuOpen = false; onOpenSettings() }, onClose = { menuOpen = false })
blocked?.let { app ->
GateSheet(app, rearAvailable,
onRear = { onLaunchRear(app); blocked = null },
onPassenger = { onConfirmPassenger(); if (onLaunch(app).allow) blocked = null },
onDismiss = { blocked = null })
}
}
}
}
@Composable
private fun Rail(page: DrivePage, gateOn: Boolean, onMenu: () -> Unit, onPage: (DrivePage) -> Unit, onToggleGate: () -> Unit) {
val p = LocalAmni.current
Column(
Modifier.width(92.dp).fillMaxHeight()
.background(Brush.verticalGradient(listOf(p.panel.copy(alpha = 0.95f), p.bg.copy(alpha = 0.9f))))
.border(width = 1.dp, brush = Brush.horizontalGradient(listOf(Color.Transparent, p.hairline)), shape = RoundedCornerShape(0.dp))
.padding(vertical = 14.dp, horizontal = 8.dp),
horizontalAlignment = Alignment.CenterHorizontally,
verticalArrangement = Arrangement.spacedBy(6.dp)
) {
Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 8.dp)) {
Box(Modifier.size(10.dp).clip(CircleShape).background(p.acc))
Spacer(Modifier.height(6.dp))
Text("AMNI", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
Text("DRIVE", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 3.sp)
}
RailBtn(Icons.Rounded.Menu, "MENU", false, onClick = onMenu)
RailBtn(Icons.Rounded.Home, "HOME", page == DrivePage.HOME) { onPage(DrivePage.HOME) }
RailBtn(Icons.Rounded.Apps, "APPS", page == DrivePage.APPS) { onPage(DrivePage.APPS) }
RailBtn(Icons.Rounded.Map, "NAV", page == DrivePage.NAV) { onPage(DrivePage.NAV) }
Spacer(Modifier.weight(1f))
RailBtn(Icons.Rounded.Security, if (gateOn) "GATE" else "OPEN", gateOn, err = !gateOn, onClick = onToggleGate)
}
}
@Composable
private fun RailBtn(icon: ImageVector, label: String, sel: Boolean, err: Boolean = false, onClick: () -> Unit) {
val p = LocalAmni.current
val tint = if (err) p.err else if (sel) p.acc else p.dim
val scale by animateFloatAsState(if (sel) 1.04f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "rail")
Column(
Modifier.scale(scale).size(76.dp, 70.dp).clip(RoundedCornerShape(18.dp))
.background(if (sel || err) Brush.verticalGradient(listOf(tint.copy(alpha = 0.18f), tint.copy(alpha = 0.06f))) else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)))
.border(1.dp, if (sel || err) tint.copy(alpha = 0.45f) else Color.Transparent, RoundedCornerShape(18.dp))
.clickable { onClick() },
horizontalAlignment = Alignment.CenterHorizontally,
verticalArrangement = Arrangement.Center
) {
Icon(icon, label, Modifier.size(26.dp), tint = tint)
Spacer(Modifier.height(4.dp))
Text(label, color = tint, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, letterSpacing = 0.8.sp)
}
}
@Composable
private fun MenuDrawer(page: DrivePage, rearAvailable: Boolean, gateOn: Boolean, weatherOn: Boolean, onPage: (DrivePage) -> Unit, onToggleGate: () -> Unit, onToggleWeather: (Boolean) -> Unit, onSettings: () -> Unit, onClose: () -> Unit) {
val p = LocalAmni.current
val ctx = LocalContext.current
Row(Modifier.fillMaxSize().background(p.scrim)) {
SoftSurface(Modifier.width(320.dp).fillMaxHeight().padding(10.dp), radius = 24.dp) {
Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
Box(Modifier.size(8.dp).clip(CircleShape).background(p.acc))
Text("AMNI DRIVE", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
Spacer(Modifier.weight(1f))
Box(Modifier.size(36.dp).clip(CircleShape).background(p.panel2).clickable { onClose() }, contentAlignment = Alignment.Center) {
Text("✕", color = p.soft, fontSize = 14.sp)
}
}
Spacer(Modifier.height(8.dp))
MenuRow(Icons.Rounded.Home, "Home", "Dashboard & map", page == DrivePage.HOME) { onPage(DrivePage.HOME) }
MenuRow(Icons.Rounded.Apps, "All Apps", "Everything installed", page == DrivePage.APPS) { onPage(DrivePage.APPS) }
MenuRow(Icons.Rounded.Map, "Navigation", "Full-screen map", page == DrivePage.NAV) { onPage(DrivePage.NAV) }
MenuRow(Icons.Rounded.MusicNote, "Media", "Open your music app", false) { runCatching { ctx.startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; onClose() }
MenuRow(Icons.Rounded.Phone, "Phone", "Dialer", false) { runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; onClose() }
MenuRow(Icons.Rounded.Tv, "Rear Screen", if (rearAvailable) "Detected — auto-routes gated apps" else "No rear display detected", false) {}
MenuRow(Icons.Rounded.Cast, "Amni Link", "Phone host · head-unit receiver", false) {
ctx.startActivity(Intent(ctx, LinkActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); onClose()
}
Box(Modifier.fillMaxWidth().height(1.dp).background(p.hairline).padding(vertical = 6.dp))
MenuSwitch(Icons.Rounded.Security, "Driver gate", gateOn) { onToggleGate() }
MenuSwitch(Icons.Rounded.WaterDrop, "Weather background", weatherOn) { onToggleWeather(it) }
Box(Modifier.fillMaxWidth().height(1.dp).background(p.hairline))
MenuRow(Icons.Rounded.Settings, "Settings", "Gate · themes · car · link", false, onSettings)
Spacer(Modifier.weight(1f))
Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
Icon(Icons.Rounded.Info, null, Modifier.size(14.dp), tint = p.dim)
Text("v1.2.0 · modern shell · AA · link", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 8.sp)
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
@Composable
private fun MediaCard(modifier: Modifier) {
val p = LocalAmni.current
val ctx = LocalContext.current
GlassCard(modifier) {
Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
AccentBar(Modifier.width(28.dp).height(3.dp))
Spacer(Modifier.width(8.dp))
Text("NOW PLAYING", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 2.sp)
}
Spacer(Modifier.weight(1f))
Box(Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(p.accDim).border(1.dp, p.acc.copy(alpha = 0.25f), RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
Icon(Icons.Rounded.MusicNote, null, Modifier.size(42.dp), tint = p.acc)
}
Spacer(Modifier.height(12.dp))
Text("Nothing playing yet", color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
Text("Your music apps take the stage here", color = p.dim, fontSize = 12.sp)
Spacer(Modifier.height(16.dp))
Box(
Modifier.clip(RoundedCornerShape(16.dp)).background(p.accentBrush)
.clickable { runCatching { ctx.startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
.padding(horizontal = 28.dp, vertical = 14.dp)
) {
Text("LAUNCH MUSIC", color = if (p.isLight) Color.White else Color(0xFF05070A), fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}
Spacer(Modifier.weight(1f))
}
}
}
@Composable
private fun WeatherCard(w: WeatherNow?) {
val p = LocalAmni.current
GlassCard(Modifier.fillMaxWidth()) {
Column(Modifier.padding(14.dp)) {
Row(verticalAlignment = Alignment.CenterVertically) {
AccentBar(Modifier.width(24.dp).height(3.dp))
Spacer(Modifier.width(8.dp))
Text("WEATHER", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 2.sp)
}
Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
Text(w?.emoji ?: "🌡️", fontSize = 34.sp)
Text("${w?.tempF ?: "--"}°", color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 32.sp, fontWeight = FontWeight.Bold)
Column {
Text(w?.label ?: "Waiting for GPS…", color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
Text("WIND ${w?.windMph ?: "--"} · HUM ${w?.humidity ?: "--"}%", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
}
}
}
}
}
@Composable
private fun TripCard(speedMph: Int, route: RouteResult?) {
val p = LocalAmni.current
GlassCard(Modifier.fillMaxWidth()) {
Column(Modifier.padding(14.dp)) {
Row(verticalAlignment = Alignment.CenterVertically) {
AccentBar(Modifier.width(24.dp).height(3.dp))
Spacer(Modifier.width(8.dp))
Text("TRIP", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 2.sp)
}
Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
Text(if (route != null) "${(route.durationS / 60).roundToInt()} MIN" else "$speedMph", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 30.sp, fontWeight = FontWeight.Bold)
Text(if (route != null) "%.1f MI".format(route.distanceM / 1609.34) else "MPH · GPS", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(bottom = 5.dp))
}
Text(if (route != null) "Search or long-press to add stops" else "Search or long-press the map to go", color = p.dim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
}
}
}
@Composable
private fun GateHintCard(cfg: DriveConfig) {
val p = LocalAmni.current
val on = cfg.masterGateEnabled && cfg.driverGateEnabled
GlassCard(Modifier.fillMaxWidth()) {
Column(Modifier.padding(14.dp)) {
Row(verticalAlignment = Alignment.CenterVertically) {
AccentBar(Modifier.width(24.dp).height(3.dp))
Spacer(Modifier.width(8.dp))
Text("GATE", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 2.sp)
Spacer(Modifier.weight(1f))
Chip(if (on) "ACTIVE" else "OFF", if (on) p.acc else p.err)
}
Text(
if (on) "Video & games pause up front while moving. Rear never gates."
else "Everything unlocked this drive. Re-arms next start.",
color = p.soft, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp)
)
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
