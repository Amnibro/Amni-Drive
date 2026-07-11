package com.amni.drive
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.amni.drive.config.DriveConfig
import com.amni.drive.gate.DriveState
import com.amni.drive.launcher.AppEntry
import com.amni.drive.link.LinkDiscovery
import com.amni.drive.link.LinkHost
import com.amni.drive.link.LinkProto
import com.amni.drive.link.LinkReceiver
import com.amni.drive.ui.AmniDriveTheme
import com.amni.drive.ui.Dashboard
import com.amni.drive.ui.LocalAmni
import com.amni.drive.ui.SettingsActivity
import com.amni.drive.ui.UiTheme
import com.amni.drive.ui.rememberPalette
import com.amni.drive.weather.WeatherNow
import com.amni.drive.weather.WeatherService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
class LinkActivity : ComponentActivity() {
private var host: LinkHost? = null
private var receiver: LinkReceiver? = null
private var discovery: LinkDiscovery? = null
override fun onCreate(savedInstanceState: Bundle?) {
super.onCreate(savedInstanceState)
val role = intent.getStringExtra(EXTRA_ROLE) ?: ROLE_PICK
val app = application as AmniDriveApp
discovery = LinkDiscovery(this)
setContent {
val theme by app.themeStore.theme.collectAsStateWithLifecycle(UiTheme())
AmniDriveTheme(rememberPalette(theme)) {
when (role) {
ROLE_HOST -> HostScreen(app, theme)
ROLE_RECV -> RecvScreen()
else -> PickScreen()
}
}
}
}
@Composable
private fun PickScreen() {
val p = LocalAmni.current
Column(Modifier.fillMaxSize().background(p.bg).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
Text("AMNI LINK", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
Text("Android Auto replacement · phone is the brain · head unit is the screen", color = p.soft, fontSize = 13.sp)
Spacer(Modifier.height(12.dp))
RoleCard("PHONE HOST", "Run Amni-Drive UI here and stream it to the car over Wi-Fi (port ${LinkProto.PORT}).") {
startActivity(Intent(this@LinkActivity, LinkActivity::class.java).putExtra(EXTRA_ROLE, ROLE_HOST))
}
RoleCard("HEAD-UNIT RECEIVER", "Install on the dash Android unit. Discovers the phone and shows the car UI full-screen.") {
startActivity(Intent(this@LinkActivity, LinkActivity::class.java).putExtra(EXTRA_ROLE, ROLE_RECV))
}
Text("Same APK · same Wi-Fi / hotspot · touch comes back to the phone", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
Text("BACK", color = p.acc, modifier = Modifier.clickable { finish() }.padding(12.dp))
}
}
@Composable
private fun RoleCard(title: String, body: String, onClick: () -> Unit) {
val p = LocalAmni.current
Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.panel2).border(1.dp, p.line, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
Text(title, color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.Bold)
Text(body, color = p.soft, fontSize = 12.sp, lineHeight = 16.sp)
}
}
@Composable
private fun HostScreen(app: AmniDriveApp, theme: UiTheme) {
val p = LocalAmni.current
val controller = remember { DriveController(this@LinkActivity, app.configStore) }
val cfg by controller.config.collectAsStateWithLifecycle(DriveConfig())
val state by controller.driveState.collectAsStateWithLifecycle(DriveState.UNKNOWN)
val passenger by controller.passengerConfirmed.collectAsStateWithLifecycle()
val location by app.tracker.location.collectAsStateWithLifecycle()
var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
var weather by remember { mutableStateOf<WeatherNow?>(null) }
var status by remember { mutableStateOf("HOST · starting") }
var linked by remember { mutableStateOf(false) }
LaunchedEffect(Unit) {
apps = controller.apps()
app.tracker.start()
val h = LinkHost()
h.status = { runOnUiThread { status = it } }
h.onClient = { runOnUiThread { linked = it } }
h.start { window.decorView.findViewById(android.R.id.content) }
host = h
discovery?.advertise { runOnUiThread { status = it } }
while (true) {
val l = app.tracker.location.value
if (l != null) { WeatherService.fetch(l.latitude, l.longitude)?.let { weather = it }; delay(900000L) } else delay(5000L)
}
}
DisposableEffect(Unit) {
onDispose {
host?.stop(); host = null
discovery?.stop()
app.tracker.stop()
}
}
Column(Modifier.fillMaxSize().background(p.bg)) {
Row(Modifier.fillMaxWidth().background(p.panel).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
Text("AMNI LINK · HOST", color = p.acc, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
Text(if (linked) "LIVE" else "WAITING", color = if (linked) p.acc else p.dim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
Text(status, color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.weight(1f), maxLines = 1)
Text("EXIT", color = p.acc, modifier = Modifier.clickable { finish() }.padding(8.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
}
Box(Modifier.weight(1f).fillMaxWidth()) {
Dashboard(
apps = apps, cfg = cfg, driveState = state, passenger = passenger, theme = theme,
location = location, weather = weather, tiles = app.tileStore, reportStore = app.reportStore,
rearAvailable = controller.rearDisplayId() != null,
onLaunch = { controller.onLaunch(it, cfg, state, passenger) },
onLaunchRear = { controller.launchRear(it) },
onToggleGate = { lifecycleScope.launch { controller.setDriverGate(!cfg.driverGateEnabled) } },
onToggleWeather = { v -> lifecycleScope.launch { app.themeStore.setWeather(v) } },
onConfirmPassenger = { controller.confirmPassenger() },
onOpenSettings = { startActivity(Intent(this@LinkActivity, SettingsActivity::class.java)) }
)
}
}
}
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RecvScreen() {
val p = LocalAmni.current
var status by remember { mutableStateOf("RECV · ready") }
var frame by remember { mutableStateOf<Bitmap?>(null) }
var linked by remember { mutableStateOf(false) }
var peers by remember { mutableStateOf(listOf<LinkDiscovery.Peer>()) }
var manualHost by remember { mutableStateOf("") }
var boxSize by remember { mutableStateOf(IntSize(1, 1)) }
DisposableEffect(Unit) {
val r = LinkReceiver()
r.status = { runOnUiThread { status = it } }
r.onFrame = { bmp -> runOnUiThread { val old = frame; frame = bmp; old?.recycle() } }
r.connected = { runOnUiThread { linked = it } }
receiver = r
discovery?.browse(
onPeer = { peer -> runOnUiThread { if (peers.none { it.host == peer.host }) peers = peers + peer } },
onStatus = { runOnUiThread { status = it } }
)
onDispose {
receiver?.stop(); receiver = null
discovery?.stop()
frame?.recycle()
}
}
fun startRecv(ip: String, port: Int = LinkProto.PORT) {
receiver?.stop()
val nr = LinkReceiver()
nr.status = { runOnUiThread { status = it } }
nr.onFrame = { bmp -> runOnUiThread { val old = frame; frame = bmp; old?.recycle() } }
nr.connected = { runOnUiThread { linked = it } }
nr.connect(ip, port)
receiver = nr
}
Column(Modifier.fillMaxSize().background(Color.Black)) {
if (!linked) {
Column(Modifier.fillMaxSize().background(p.bg).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
Text("AMNI LINK · RECEIVER", color = p.acc, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp)
Text(status, color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
Text("Hosts on this network", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
items(peers) { peer ->
Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.panel2).clickable { startRecv(peer.host, peer.port) }.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
Text(peer.name, color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
Text("${peer.host}:${peer.port}", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
}
}
}
Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
OutlinedTextField(value = manualHost, onValueChange = { manualHost = it }, label = { Text("IP of phone") }, modifier = Modifier.weight(1f), singleLine = true)
Text("CONNECT", color = Color.Black, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(p.acc).clickable {
val ip = manualHost.trim(); if (ip.isNotEmpty()) startRecv(ip)
}.padding(horizontal = 14.dp, vertical = 12.dp), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
}
Text("BACK", color = p.acc, modifier = Modifier.clickable { finish() })
}
} else {
Box(Modifier.fillMaxSize().onSizeChanged { boxSize = it }.pointerInteropFilter { ev ->
val w = boxSize.width.coerceAtLeast(1).toFloat()
val h = boxSize.height.coerceAtLeast(1).toFloat()
val x = (ev.x / w).coerceIn(0f, 1f)
val y = (ev.y / h).coerceIn(0f, 1f)
val action = when (ev.actionMasked) {
MotionEvent.ACTION_DOWN -> LinkProto.TOUCH_DOWN
MotionEvent.ACTION_MOVE -> LinkProto.TOUCH_MOVE
MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> LinkProto.TOUCH_UP
else -> LinkProto.TOUCH_CANCEL
}
receiver?.sendTouch(action, x, y)
true
}) {
val bmp = frame
if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
else Text(status, color = Color.White, modifier = Modifier.align(Alignment.Center), fontFamily = FontFamily.Monospace)
Text("LIVE  $status", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(Color.Black.copy(alpha = 0.5f)).padding(6.dp))
Text("EXIT", color = p.acc, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clickable { finish() }.background(Color.Black.copy(alpha = 0.5f)).padding(6.dp), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
}
}
}
}
override fun onDestroy() {
host?.stop(); receiver?.stop(); discovery?.stop()
super.onDestroy()
}
companion object {
const val EXTRA_ROLE = "role"
const val ROLE_PICK = "pick"
const val ROLE_HOST = "host"
const val ROLE_RECV = "recv"
}
}
