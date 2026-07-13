package com.amni.drive
import android.content.Context
import com.amni.drive.config.ConfigStore
import com.amni.drive.config.DriveConfig
import com.amni.drive.display.DisplayRole
import com.amni.drive.display.DisplayRouter
import com.amni.drive.gate.DriveState
import com.amni.drive.gate.GateDecision
import com.amni.drive.gate.GateManager
import com.amni.drive.gate.GateResolver
import com.amni.drive.gate.speed.CarPropertyMotionSource
import com.amni.drive.gate.speed.GpsMotionSource
import com.amni.drive.gate.speed.ManualMotionSource
import com.amni.drive.gate.speed.MotionConfig
import com.amni.drive.gate.speed.MotionMonitor
import com.amni.drive.gate.speed.MotionSource
import com.amni.drive.launcher.AppEntry
import com.amni.drive.launcher.AppInventory
import com.amni.drive.launcher.AppLauncher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
class DriveController(context: Context, private val store: ConfigStore) {
    private val app = context.applicationContext
    val manual = ManualMotionSource()
    private val sources: List<MotionSource> = buildSources()
    private val monitor = MotionMonitor(sources, MotionConfig())
    private val router = DisplayRouter(app)
    private val launcher = AppLauncher(app)
    private val inventory = AppInventory(app)
    private val _passenger = MutableStateFlow(false)
    val passengerConfirmed: StateFlow<Boolean> = _passenger
    val config: Flow<DriveConfig> = store.config
    val driveState: Flow<DriveState> = monitor.state()
    suspend fun apps(): List<AppEntry> = inventory.load()
    fun confirmPassenger() { _passenger.value = true }
    fun clearPassenger() { _passenger.value = false }
    fun rearDisplayId(): Int? = router.rearDisplayId()
    suspend fun setDriverGate(enabled: Boolean) { store.setDriver(enabled); store.setMaster(enabled) }
    fun launchRear(entry: AppEntry): Boolean {
        val rearId = rearDisplayId() ?: return false
        launcher.launch(entry, rearId)
        return true
    }
    fun onLaunch(entry: AppEntry, cfg: DriveConfig, state: DriveState, passenger: Boolean, surfaceDisplayId: Int? = null): GateDecision {
        val rearId = if (cfg.autoRouteRearDisplay) rearDisplayId() else null
        val useRear = entry.category.distracting && rearId != null && surfaceDisplayId == null
        val role = if (useRear) DisplayRole.REAR_PASSENGER else DisplayRole.PRIMARY_DRIVER
        val decision = GateManager.decide(GateResolver.input(entry.category, role, state, cfg, passenger))
        if (decision.allow) {
            val target = when {
                surfaceDisplayId != null -> surfaceDisplayId
                useRear -> rearId
                else -> null
            }
            val ok = launcher.launch(entry, target)
            if (!ok && surfaceDisplayId != null) launcher.launch(entry, null)
        }
        return decision
    }
    private fun buildSources(): List<MotionSource> {
        val list = mutableListOf<MotionSource>(GpsMotionSource(app), manual)
        if (CarPropertyMotionSource.isAvailable()) list.add(0, CarPropertyMotionSource(app))
        return list
    }
}
