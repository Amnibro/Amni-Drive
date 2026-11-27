package com.amni.drive
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
private var forceTutorial by mutableStateOf(false)
@Volatile private var huMode = false
override fun onCreate(savedInstanceState: Bundle?) {
super.onCreate(savedInstanceState)
val app = application as AmniDriveApp
controller = DriveController(this, app.configStore)
requestLocation()
applyDriveIntent(intent)
setContent {
val theme by app.themeStore.theme.collectAsStateWithLifecycle(UiTheme())
val cfgState by controller.config.collectAsStateWithLifecycle( null )
val state by controller.driveState.collectAsStateWithLifecycle(DriveState.UNKNOWN)
val passenger by controller.passengerConfirmed.collectAsStateWithLifecycle()
val location by app.tracker.location.collectAsStateWithLifecycle()
val ota by com.amni.drive.update.OtaUpdater.state.collectAsStateWithLifecycle()
var dismissOtaBanner by remember { mutableStateOf(false) }
var unsentCrash by remember { mutableStateOf(com.amni.drive.diag.CrashReporter.latestUnsent(this@MainActivity)) }
var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
var weather by remember { mutableStateOf<WeatherNow?>(null) }
val cfg = cfgState ?: DriveConfig(onboardingCompleted = true)
LaunchedEffect(Unit) { apps = controller.apps() }
LaunchedEffect(state) { if (state == DriveState.MOVING) controller.clearPassenger() }
LaunchedEffect(Unit) {
while (true) {
val l = app.tracker.location.value
if (l != null) { WeatherService.fetch(l.latitude, l.longitude)?.let { weather = it }; delay(900000L) } else delay(5000L)
}
}
AmniDriveTheme(rememberPalette(theme)) {
if (cfgState != null && (!cfg.onboardingCompleted || forceTutorial)) {
com.amni.drive.ui.OnboardingWizard(
theme = theme,
themeStore = app.themeStore,
configStore = app.configStore,
onFinish = {
forceTutorial = false
lifecycleScope.launch { apps = controller.apps() }
}
)
} else {
androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
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
onOpenTutorial = { forceTutorial = true },
initialPage = startPage,
onSetTileLayout = { v -> lifecycleScope.launch { app.themeStore.setTileLayout(v) } },
onSetWidgets = { v -> lifecycleScope.launch { app.themeStore.setWidgets(v) } },
onSetWidgetWidthMode = { v -> lifecycleScope.launch { app.themeStore.setWidgetWidthMode(v) } },
onSetWidgetCompact = { v -> lifecycleScope.launch { app.themeStore.setWidgetCompact(v) } },
onSetDockPosition = { v -> lifecycleScope.launch { app.themeStore.setDockPosition(v) } },
onSetMapSplitRatio = { v -> lifecycleScope.launch { app.themeStore.setMapSplitRatio(v) } },
onSetWidgetRatio = { v -> lifecycleScope.launch { app.themeStore.setWidgetRatio(v) } },
onSetSwapMapMedia = { v -> lifecycleScope.launch { app.themeStore.setSwapMapMedia(v) } },
onSetCarUiScale = { v -> lifecycleScope.launch { app.themeStore.setCarUiScale(v) } },
onSetFontScale = { v -> lifecycleScope.launch { app.themeStore.setFontScale(v) } },
onCycleTheme = {
lifecycleScope.launch {
val next = com.amni.drive.ui.AmniPreset.entries[(theme.preset.ordinal + 1) % com.amni.drive.ui.AmniPreset.entries.size]
app.themeStore.setPreset(next)
}
}
)
val crashFile = unsentCrash
if (crashFile != null) {
val p = com.amni.drive.ui.LocalAmni.current
androidx.compose.foundation.layout.Row(
modifier = androidx.compose.ui.Modifier
.align(androidx.compose.ui.Alignment.TopCenter)
.padding(top = 10.dp)
.clip(androidx.compose.foundation.shape.RoundedCornerShape(99.dp))
.background(p.panel)
.border(1.5.dp, p.gold, androidx.compose.foundation.shape.RoundedCornerShape(99.dp))
.clickable {
startActivity(
Intent(this@MainActivity, com.amni.drive.ui.CrashReportActivity::class.java)
.putExtra(com.amni.drive.ui.CrashReportActivity.EXTRA_PATH, crashFile.absolutePath)
.putExtra(com.amni.drive.ui.CrashReportActivity.EXTRA_CRASH, true)
)
unsentCrash = null
}
.padding(horizontal = 14.dp, vertical = 7.dp),
verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
) {
androidx.compose.material3.Text(
"⚠ Crash log captured — tap to email ${com.amni.drive.diag.CrashReporter.SUPPORT_EMAIL}",
color = p.gold,
fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
fontSize = 11.sp,
fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
)
androidx.compose.material3.Text(
"✕",
color = p.soft,
fontSize = 12.sp,
modifier = androidx.compose.ui.Modifier.clickable {
com.amni.drive.diag.CrashReporter.dismissUnsent(this@MainActivity)
unsentCrash = null
}.padding(horizontal = 4.dp)
)
}
} else if (ota.totalUpdatesCount > 0 && !dismissOtaBanner) {
val p = com.amni.drive.ui.LocalAmni.current
val bannerMsg = if (ota.selfUpdateAvailable && ota.selfRelease != null) {
"⬆ Amni-Drive v${ota.selfRelease!!.versionName} OTA ready — tap to update"
} else {
"⬆ ${ota.totalUpdatesCount} Amni companion app update${if (ota.totalUpdatesCount > 1) "s" else ""} ready — tap to install"
}
androidx.compose.foundation.layout.Row(
modifier = androidx.compose.ui.Modifier
.align(androidx.compose.ui.Alignment.TopCenter)
.padding(top = 10.dp)
.clip(androidx.compose.foundation.shape.RoundedCornerShape(99.dp))
.background(p.panel)
.border(1.5.dp, p.acc, androidx.compose.foundation.shape.RoundedCornerShape(99.dp))
.clickable {
startActivity(
Intent(this@MainActivity, SettingsActivity::class.java)
.putExtra(SettingsActivity.EXTRA_TAB, "UPDATES")
)
}
.padding(horizontal = 14.dp, vertical = 7.dp),
verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
) {
androidx.compose.material3.Text(
bannerMsg,
color = p.acc,
fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
fontSize = 11.sp,
fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
)
androidx.compose.material3.Text(
"✕",
color = p.soft,
fontSize = 12.sp,
modifier = androidx.compose.ui.Modifier.clickable { dismissOtaBanner = true }.padding(horizontal = 4.dp)
)
}
}
}
}
}
}
}
override fun onNewIntent(intent: Intent) {
super.onNewIntent(intent)
setIntent(intent)
applyDriveIntent(intent)
}
override fun onStart() { super.onStart(); (application as AmniDriveApp).tracker.start() }
override fun onStop() { super.onStop() }
private fun applyDriveIntent(intent: Intent?) {
if (intent?.getBooleanExtra(EXTRA_TUTORIAL, false) == true) {
forceTutorial = true
}
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
private val locPermLauncher = registerForActivityResult(
androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
) { res ->
if (res.values.any { it }) {
(application as AmniDriveApp).tracker.start()
com.amni.drive.wire.AoaProbeService.refreshForeground(this)
}
}
private fun requestLocation() {
if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
locPermLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
} else {
(application as AmniDriveApp).tracker.start()
}
}
companion object {
const val EXTRA_PAGE = "amni_page"
const val EXTRA_DRIVE_MODE = "amni_drive_mode"
const val EXTRA_START_PROJECTION = "amni_start_projection"
const val EXTRA_START_AOA = "amni_start_aoa"
const val EXTRA_TUTORIAL = "amni_open_tutorial"
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
