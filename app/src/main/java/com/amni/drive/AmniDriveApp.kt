package com.amni.drive
import android.app.Application
import android.os.Build
import com.amni.drive.auto.CarConnectionService
import com.amni.drive.config.ConfigStore
import com.amni.drive.diag.CrashReporter
import com.amni.drive.map.LocationTracker
import com.amni.drive.map.ReportStore
import com.amni.drive.map.TileStore
import com.amni.drive.ui.AmniMapStudio
import com.amni.drive.ui.ThemeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
class AmniDriveApp : Application() {
    lateinit var configStore: ConfigStore
        private set
    lateinit var themeStore: ThemeStore
        private set
    lateinit var tileStore: TileStore
        private set
    lateinit var reportStore: ReportStore
        private set
    lateinit var tracker: LocationTracker
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private fun isCrashProcess(): Boolean {
        val pn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) getProcessName() else ""
        return pn.endsWith(":crash")
    }
    override fun onCreate() {
        super.onCreate()
        if (isCrashProcess()) return
        CrashReporter.install(this)
        CrashReporter.breadcrumb("App", "onCreate v${BuildConfig.VERSION_NAME}")
        configStore = ConfigStore(this)
        themeStore = ThemeStore(this)
        tileStore = TileStore(this)
        reportStore = ReportStore(this)
        tracker = LocationTracker(this)
        AmniMapStudio.ensureLoaded(this)
        tracker.start()
        appScope.launch {
            var lastLocTs = 0L
            com.amni.drive.wire.AaPostTls.carSensors.collect { sens ->
                val cl = sens.location ?: return@collect
                if (cl.receivedAtMs != lastLocTs) {
                    lastLocTs = cl.receivedAtMs
                    tracker.injectCarLocation(cl.lat, cl.lon, cl.accuracyM, cl.speedMps ?: sens.speedMps, cl.bearingDeg, cl.altitudeM)
                }
            }
        }
        com.amni.drive.launcher.CarApps.probe()
        com.amni.drive.launcher.CarMirror.setup(this)
        com.amni.drive.map.CustomPlaces.init(this)
        appScope.launch(Dispatchers.IO) { runCatching { com.amni.drive.wire.AaCertStore.ensureReady(this@AmniDriveApp) } }
        appScope.launch(Dispatchers.IO) { runCatching { com.amni.drive.update.OtaUpdater.checkIfDue(this@AmniDriveApp) } }
        appScope.launch { runCatching { configStore.applyGateOffDefaultOnce() } }
        runCatching { com.amni.drive.wire.CarLink.start(this) }
        runCatching { CarConnectionService.start(this) }
        runCatching { com.amni.drive.wire.AaSessionWatchService.start(this) }
        runCatching { com.amni.drive.wire.MeshDevBridgeService.start(this) }
        runCatching { com.amni.drive.media.MediaSessionBridge.start(this) }
    }
}
