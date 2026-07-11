package com.amni.drive.wire
import android.app.Activity
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
class AoaAttachActivity : Activity() {
override fun onCreate(savedInstanceState: Bundle?) {
super.onCreate(savedInstanceState)
val acc = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
else @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
Log.i(AoaProbeService.TAG, "AoaAttachActivity accessory=$acc")
AoaProbeService.start(this)
AoaProbeService.poll(this)
finish()
}
}
