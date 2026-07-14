package com.amni.drive
import android.app.Application
import com.amni.drive.auto.CarConnectionService
import com.amni.drive.config.ConfigStore
import com.amni.drive.map.LocationTracker
import com.amni.drive.map.ReportStore
import com.amni.drive.map.TileStore
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
    override fun onCreate() {
        super.onCreate()
        configStore = ConfigStore(this)
        themeStore = ThemeStore(this)
        tileStore = TileStore(this)
        reportStore = ReportStore(this)
        tracker = LocationTracker(this)
        appScope.launch { runCatching { configStore.applyGateOffDefaultOnce() } }
        runCatching { com.amni.drive.wire.CarLink.start(this) }
        runCatching { CarConnectionService.start(this) }
        runCatching { com.amni.drive.wire.AaSessionWatchService.start(this) }
        runCatching { com.amni.drive.wire.MeshDevBridgeService.start(this) }
        runCatching { com.amni.drive.media.MediaSessionBridge.start(this) }
    }
}
