package com.amni.drive.wire
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
class AoaBulkTransport(
private val usb: UsbManager,
private val accessory: UsbAccessory
) : AaTransport {
override val name: String = "AOA:${accessory.model}"
private var pfd: ParcelFileDescriptor? = null
private var inp: FileInputStream? = null
private var out: FileOutputStream? = null
override fun open(): Boolean {
if (!usb.hasPermission(accessory)) return false
return try {
val fd = usb.openAccessory(accessory) ?: return false
pfd = fd
inp = FileInputStream(fd.fileDescriptor)
out = FileOutputStream(fd.fileDescriptor)
true
} catch (_: Exception) {
close()
false
}
}
override fun close() {
runCatching { inp?.close() }
runCatching { out?.close() }
runCatching { pfd?.close() }
inp = null; out = null; pfd = null
}
override fun input(): InputStream? = inp
override fun output(): OutputStream? = out
override fun isOpen(): Boolean = pfd != null
}
