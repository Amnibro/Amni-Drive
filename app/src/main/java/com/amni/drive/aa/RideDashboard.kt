package com.amni.drive.aa
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.amni.drive.AmniDriveApp
import com.amni.drive.DriveController
import com.amni.drive.config.DriveConfig
import com.amni.drive.gate.DriveState
import com.amni.drive.launcher.AppEntry
import com.amni.drive.ui.AmniDriveTheme
import com.amni.drive.ui.Dashboard
import com.amni.drive.ui.DrivePage
import com.amni.drive.ui.UiTheme
import com.amni.drive.ui.rememberPalette
import com.amni.drive.weather.WeatherNow
import com.amni.drive.weather.WeatherService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
@Composable
fun RideDashboard(
app: AmniDriveApp,
controller: DriveController,
initialPage: DrivePage = DrivePage.HOME,
surfaceDisplayId: Int? = null
) {
val theme by app.themeStore.theme.collectAsState(UiTheme())
val cfg by controller.config.collectAsState(DriveConfig())
val state by controller.driveState.collectAsState(DriveState.UNKNOWN)
val passenger by controller.passengerConfirmed.collectAsState()
val rawLocation by app.tracker.location.collectAsState()
var location by remember { mutableStateOf<android.location.Location?>(null) }
LaunchedEffect(rawLocation) {
val nl = rawLocation ?: return@LaunchedEffect
val cur = location
if (cur == null) {
location = nl
} else {
val oldMph = (cur.speed * 2.23694f).toInt()
val newMph = (nl.speed * 2.23694f).toInt()
val movedM = cur.distanceTo(nl)
val degDiff = kotlin.math.abs(((nl.bearing - cur.bearing + 540f) % 360f) - 180f)
if (oldMph != newMph || movedM >= 1.5f || degDiff >= 3f || nl.time - cur.time >= 1500L) {
location = nl
}
}
}
var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
var weather by remember { mutableStateOf<WeatherNow?>(null) }
val scope = rememberCoroutineScope()
LaunchedEffect(Unit) {
app.tracker.start()
apps = controller.apps()
}
LaunchedEffect(state) { if (state == DriveState.MOVING) controller.clearPassenger() }
LaunchedEffect(Unit) {
while (true) {
val l = app.tracker.location.value
if (l != null) {
WeatherService.fetch(l.latitude, l.longitude)?.let { weather = it }
delay(900_000L)
} else delay(5_000L)
}
}
val baseDens = androidx.compose.ui.platform.LocalDensity.current
val uiScale = theme.carUiScale.coerceIn(0.80f, 1.40f)
androidx.compose.runtime.CompositionLocalProvider(
androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(baseDens.density * uiScale, fontScale = 1.14f)
) {
AmniDriveTheme(rememberPalette(theme)) {
Dashboard(
apps = apps,
cfg = cfg,
driveState = state,
passenger = passenger,
theme = theme,
location = location,
weather = weather,
tiles = app.tileStore,
reportStore = app.reportStore,
rearAvailable = false,
onLaunch = { controller.onLaunch(it, cfg, state, passenger, surfaceDisplayId) },
onLaunchRear = { },
onToggleGate = {
val eff = cfg.driverGateEnabled && cfg.masterGateEnabled
android.util.Log.i("Amni.Gate", "toggle eff=$eff driver=${cfg.driverGateEnabled} master=${cfg.masterGateEnabled}")
scope.launch { controller.setDriverGate(!eff) }
},
onToggleWeather = { v -> scope.launch { app.themeStore.setWeather(v) } },
onConfirmPassenger = { controller.confirmPassenger() },
onOpenSettings = { },
initialPage = initialPage,
carSurface = true,
surfaceDisplayId = surfaceDisplayId,
onSetTileLayout = { v -> scope.launch { app.themeStore.setTileLayout(v) } },
onSetAppShape = { v -> scope.launch { app.themeStore.setAppShape(v) } },
onSetWidgets = { v -> scope.launch { app.themeStore.setWidgets(v) } },
onSetWidgetWidthMode = { v -> scope.launch { app.themeStore.setWidgetWidthMode(v) } },
onSetWidgetCompact = { v -> scope.launch { app.themeStore.setWidgetCompact(v) } },
onSetDockPosition = { v -> scope.launch { app.themeStore.setDockPosition(v) } },
onSetMapSplitRatio = { v -> scope.launch { app.themeStore.setMapSplitRatio(v) } },
onSetWidgetRatio = { v -> scope.launch { app.themeStore.setWidgetRatio(v) } },
onSetSwapMapMedia = { v -> scope.launch { app.themeStore.setSwapMapMedia(v) } },
onSetCarUiScale = { v -> scope.launch { app.themeStore.setCarUiScale(v) } },
onSetFontScale = { v -> scope.launch { app.themeStore.setFontScale(v) } },
onCycleTheme = {
android.util.Log.i("Amni.Theme", "cycle from ${theme.preset}")
scope.launch {
runCatching { app.themeStore.setPreset(com.amni.drive.ui.AmniPreset.entries[(theme.preset.ordinal + 1) % com.amni.drive.ui.AmniPreset.entries.size]) }
.onFailure { android.util.Log.e("Amni.Theme", "setPreset fail ${it.message}", it) }
}
}
)
}
}
}
