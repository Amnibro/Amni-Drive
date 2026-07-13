package com.amni.drive.wire
import android.app.Activity
import android.app.ActivityManager
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.amni.drive.config.ConfigStore
import com.amni.drive.config.DriveConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
class AoaAttachActivity : Activity() {
override fun onCreate(savedInstanceState: Bundle?) {
super.onCreate(savedInstanceState)
val acc = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
else @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
val mode = try {
runBlocking { ConfigStore(this@AoaAttachActivity).config.first().aaPathMode }
} catch (_: Exception) {
DriveConfig.PATH_RIDE
}
val gearhead = gearheadEnabled()
Log.i(AoaProbeService.TAG, "AoaAttachActivity mode=$mode gearheadEnabled=$gearhead accessory=$acc")
AaSessionWatchService.tick(this)
if (mode == DriveConfig.PATH_OWN) {
Log.i(AoaProbeService.TAG, "OWN mode · probe on attach")
AoaProbeService.claim(this)
} else {
Log.i(AoaProbeService.TAG, "RIDE passive · never claim AOA (gearhead owns USB · no red screen)")
}
finish()
}
private fun gearheadEnabled(): Boolean = try {
packageManager.getApplicationEnabledSetting("com.google.android.projection.gearhead").let {
it != android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
it != android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER &&
it != android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
}
} catch (_: Exception) {
try {
@Suppress("DEPRECATION")
(getSystemService(ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses?.any {
it.processName.startsWith("com.google.android.projection.gearhead")
} == true
} catch (_: Exception) { true }
}
}
