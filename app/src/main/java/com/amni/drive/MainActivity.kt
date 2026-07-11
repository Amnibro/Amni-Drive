package com.amni.drive
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.amni.drive.config.DriveConfig
import com.amni.drive.gate.DriveState
import com.amni.drive.launcher.AppEntry
import com.amni.drive.ui.AmniDriveTheme
import com.amni.drive.ui.Dashboard
import com.amni.drive.ui.SettingsActivity
import com.amni.drive.ui.UiTheme
import com.amni.drive.ui.rememberPalette
import com.amni.drive.weather.WeatherNow
import com.amni.drive.weather.WeatherService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
class MainActivity : ComponentActivity() {
    private lateinit var controller: DriveController
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as AmniDriveApp
        controller = DriveController(this, app.configStore)
        requestLocation()
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
                    onOpenSettings = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
                )
            }
        }
    }
    override fun onStart() { super.onStart(); (application as AmniDriveApp).tracker.start() }
    override fun onStop() { super.onStop(); (application as AmniDriveApp).tracker.stop() }
    private fun requestLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
    }
}
