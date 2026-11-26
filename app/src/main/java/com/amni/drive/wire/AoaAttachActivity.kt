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
val projecting = CarLink.projecting
Log.i(AoaProbeService.TAG, "AoaAttachActivity projecting=$projecting accessory=$acc")
AaSessionWatchService.tick(this)
AoaProbeService.onAttach(this, acc)
finish()
}
}
