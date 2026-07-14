package com.amni.drive
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.amni.drive.config.DriveConfig
import com.amni.drive.gate.DriveState
import com.amni.drive.launcher.AppEntry
import com.amni.drive.ui.AmniDriveTheme
import com.amni.drive.ui.Dashboard
import com.amni.drive.ui.DrivePage
import com.amni.drive.ui.SettingsActivity
import com.amni.drive.ui.UiTheme
import com.amni.drive.ui.rememberPalette
import com.amni.drive.weather.WeatherNow
import com.amni.drive.weather.WeatherService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
class MainActivity : ComponentActivity() {
private lateinit var controller: DriveController
private var startPage by mutableStateOf(DrivePage.HOME)
@Volatile private var huMode = false
override fun onCreate(savedInstanceState: Bundle?) {
super.onCreate(savedInstanceState)
val app = application as AmniDriveApp
controller = DriveController(this, app.configStore)
requestLocation()
applyDriveIntent(intent)
setContent {
val theme by app.themeStore.theme.collectAsStateWithLifecycle(UiTheme())
val cfg by controller.config.collectAsStateWithLifecycle(DriveConfig())
val state by controller.driveState.collectAsStateWithLifecycle(DriveState.UNKNOWN)
val passenger by controller.passengerConfirmed.collectAsStateWithLifecycle()
val location by app.tracker.location.collectAsStateWithLifecycle()
var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
var weather by remember { mutableStateOf<WeatherNow?>(null) }
LaunchedEffect(Unit) { apps = controller.apps() }
LaunchedEffect(state) { if (state == DriveState.MOVING) controller.clearPassenger() }
LaunchedEffect(Unit) {
while (true) {
val l = app.tracker.location.value
if (l != null) { WeatherService.fetch(l.latitude, l.longitude)?.let { weather = it }; delay(900000L) } else delay(5000L)
}
}
AmniDriveTheme(rememberPalette(theme)) {
Dashboard(
apps = apps, cfg = cfg, driveState = state, passenger = passenger, theme = theme,
location = location, weather = weather, tiles = app.tileStore, reportStore = app.reportStore,
rearAvailable = controller.rearDisplayId() != null,
onLaunch = { controller.onLaunch(it, cfg, state, passenger) },
onLaunchRear = { controller.launchRear(it) },
onToggleGate = { lifecycleScope.launch { controller.setDriverGate(!cfg.driverGateEnabled) } },
onToggleWeather = { v -> lifecycleScope.launch { app.themeStore.setWeather(v) } },
onConfirmPassenger = { controller.confirmPassenger() },
onOpenSettings = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) },
initialPage = startPage,
onSetTileLayout = { v -> lifecycleScope.launch { app.themeStore.setTileLayout(v) } }
)
}
}
}
override fun onNewIntent(intent: Intent) {
super.onNewIntent(intent)
setIntent(intent)
applyDriveIntent(intent)
}
override fun onStart() { super.onStart(); (application as AmniDriveApp).tracker.start() }
override fun onStop() { super.onStop(); (application as AmniDriveApp).tracker.stop() }
private fun applyDriveIntent(intent: Intent?) {
val page = when (intent?.getStringExtra(EXTRA_PAGE)?.lowercase()) {
"nav", "navigation", "map" -> DrivePage.NAV
"apps" -> DrivePage.APPS
else -> if (intent?.action == ACTION_OPEN_NAV || intent?.action == ACTION_OWN_STACK) DrivePage.NAV else DrivePage.HOME
}
startPage = page
val drive = intent?.getBooleanExtra(EXTRA_DRIVE_MODE, false) == true || page == DrivePage.NAV
if (drive) {
window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
setShowWhenLocked(true)
setTurnScreenOn(true)
} else {
@Suppress("DEPRECATION")
window.addFlags(
WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
)
}
WindowCompat.setDecorFitsSystemWindows(window, false)
lifecycleScope.launch {
huMode = runCatching { (application as AmniDriveApp).configStore.config.first().huFullscreen }.getOrDefault(true)
if (huMode) applyImmersive()
}
}
if (intent?.getBooleanExtra(EXTRA_START_PROJECTION, false) == true || intent?.action == ACTION_OWN_STACK) {
runCatching { com.amni.drive.wire.ProjectionHostService.start(this) }
}
if (intent?.getBooleanExtra(EXTRA_START_AOA, false) == true || intent?.action == ACTION_OWN_STACK) {
runCatching { com.amni.drive.wire.AoaProbeService.claim(this) }
}
}
private fun applyImmersive() {
val c = WindowInsetsControllerCompat(window, window.decorView)
c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
c.hide(WindowInsetsCompat.Type.systemBars())
}
override fun onWindowFocusChanged(hasFocus: Boolean) {
super.onWindowFocusChanged(hasFocus)
if (hasFocus && huMode) applyImmersive()
}
private fun requestLocation() {
if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
}
companion object {
const val EXTRA_PAGE = "amni_page"
const val EXTRA_DRIVE_MODE = "amni_drive_mode"
const val EXTRA_START_PROJECTION = "amni_start_projection"
const val EXTRA_START_AOA = "amni_start_aoa"
const val ACTION_OPEN_NAV = "com.amni.drive.action.OPEN_NAV"
const val ACTION_OWN_STACK = "com.amni.drive.action.OWN_STACK"
fun openNavIntent(from: android.content.Context): Intent =
Intent(from, MainActivity::class.java)
.setAction(ACTION_OPEN_NAV)
.putExtra(EXTRA_PAGE, "nav")
.putExtra(EXTRA_DRIVE_MODE, true)
.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_CLEAR_TOP)
fun ownStackIntent(from: android.content.Context): Intent =
Intent(from, MainActivity::class.java)
.setAction(ACTION_OWN_STACK)
.putExtra(EXTRA_PAGE, "nav")
.putExtra(EXTRA_DRIVE_MODE, true)
.putExtra(EXTRA_START_PROJECTION, true)
.putExtra(EXTRA_START_AOA, true)
.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}
}
