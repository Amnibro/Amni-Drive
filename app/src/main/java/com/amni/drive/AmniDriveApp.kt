package com.amni.drive
import android.app.Application
import com.amni.drive.config.ConfigStore
import com.amni.drive.map.LocationTracker
import com.amni.drive.map.ReportStore
import com.amni.drive.map.TileStore
import com.amni.drive.ui.ThemeStore
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
    override fun onCreate() {
        super.onCreate()
        configStore = ConfigStore(this)
        themeStore = ThemeStore(this)
        tileStore = TileStore(this)
        reportStore = ReportStore(this)
        tracker = LocationTracker(this)
    }
}
