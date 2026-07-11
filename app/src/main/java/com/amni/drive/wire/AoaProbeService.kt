package com.amni.drive.wire
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
class AoaProbeService : Service() {
private var receiver: BroadcastReceiver? = null
private var accessoryFd: ParcelFileDescriptor? = null
override fun onBind(intent: Intent?): IBinder? = null
override fun onCreate() {
super.onCreate()
ensureChannel()
startForeground(NOTIF_ID, buildNotif("AOA probe idle"))
val filter = IntentFilter().apply {
addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
addAction(ACTION_USB_STATE)
}
receiver = object : BroadcastReceiver() {
override fun onReceive(ctx: Context?, intent: Intent?) {
if (intent == null) return
when (intent.action) {
UsbManager.ACTION_USB_ACCESSORY_ATTACHED -> {
val acc = readAccessory(intent)
log("ATTACHED ${desc(acc)}")
tryOpen(acc)
}
UsbManager.ACTION_USB_ACCESSORY_DETACHED -> {
val acc = readAccessory(intent)
log("DETACHED ${desc(acc)}")
closeAccessory()
}
ACTION_USB_STATE -> {
val connected = intent.getBooleanExtra("connected", false)
val configured = intent.getBooleanExtra("configured", false)
val accessory = intent.getBooleanExtra("accessory", false)
log("USB_STATE connected=$connected configured=$configured accessory=$accessory")
if (accessory) pollAccessories()
}
}
}
}
if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
else @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(receiver, filter)
pollAccessories()
log("probe service started")
}
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
if (intent?.action == ACTION_POLL) pollAccessories()
return START_STICKY
}
override fun onDestroy() {
runCatching { unregisterReceiver(receiver) }
closeAccessory()
super.onDestroy()
}
private fun pollAccessories() {
val usb = getSystemService(USB_SERVICE) as UsbManager
val list = usb.accessoryList
if (list.isNullOrEmpty()) {
log("poll: no accessories")
updateNotif("AOA probe · no accessory")
return
}
list.forEach { acc ->
log("poll: ${desc(acc)} perm=${usb.hasPermission(acc)}")
if (usb.hasPermission(acc)) tryOpen(acc)
}
}
private fun tryOpen(acc: UsbAccessory?) {
if (acc == null) return
val usb = getSystemService(USB_SERVICE) as UsbManager
if (!usb.hasPermission(acc)) {
log("no permission for ${desc(acc)} (gearhead may own session)")
updateNotif("AOA seen · no permission (gearhead owns?)")
return
}
closeAccessory()
try {
val pfd = usb.openAccessory(acc)
if (pfd == null) {
log("openAccessory returned null for ${desc(acc)}")
updateNotif("AOA open failed")
return
}
accessoryFd = pfd
log("OPENED accessory fd=${pfd.fd} ${desc(acc)}")
updateNotif("AOA OPEN · ${acc.model}")
Thread({ sniff(pfd) }, "aoa-sniff").also { it.isDaemon = true; it.start() }
} catch (e: Exception) {
log("open error: ${e.message}")
updateNotif("AOA error ${e.message}")
}
}
private fun sniff(pfd: ParcelFileDescriptor) {
try {
val inp = ParcelFileDescriptor.AutoCloseInputStream(pfd)
val buf = ByteArray(4096)
var total = 0
while (accessoryFd != null) {
val n = try { inp.read(buf) } catch (_: Exception) { break }
if (n <= 0) break
total += n
val hex = buf.take(n.coerceAtMost(32)).joinToString(" ") { b -> "%02x".format(b) }
log("RX $n bytes (total=$total) head=$hex")
if (total > 256_000) break
}
log("sniff end total=$total")
} catch (e: Exception) {
log("sniff error ${e.message}")
}
}
private fun closeAccessory() {
try { accessoryFd?.close() } catch (_: Exception) {}
accessoryFd = null
}
private fun readAccessory(intent: Intent): UsbAccessory? =
if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
else @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
private fun desc(acc: UsbAccessory?): String {
if (acc == null) return "null"
return "mfr=${acc.manufacturer} model=${acc.model} ver=${acc.version} desc=${acc.description} uri=${acc.uri} sn=${acc.serial}"
}
private fun log(msg: String) {
Log.i(TAG, msg)
try {
val dir = File(filesDir, "aoa_probe")
if (!dir.exists()) dir.mkdirs()
val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
val line = "${SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())} $msg\n"
FileOutputStream(File(dir, "probe-$day.log"), true).use { it.write(line.toByteArray()) }
} catch (_: Exception) {}
}
private fun ensureChannel() {
if (Build.VERSION.SDK_INT < 26) return
val nm = getSystemService(NotificationManager::class.java)
nm.createNotificationChannel(NotificationChannel(CHANNEL, "Amni Wire AOA", NotificationManager.IMPORTANCE_LOW))
}
private fun buildNotif(text: String): Notification {
val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
return b.setContentTitle("Amni Wire")
.setContentText(text)
.setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
.setOngoing(true)
.build()
}
private fun updateNotif(text: String) {
val nm = getSystemService(NotificationManager::class.java)
nm.notify(NOTIF_ID, buildNotif(text))
}
companion object {
const val TAG = "Amni.AoaProbe"
const val CHANNEL = "amni_wire_aoa"
const val NOTIF_ID = 7731
const val ACTION_POLL = "com.amni.drive.wire.POLL_AOA"
const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"
fun start(ctx: Context) {
val i = Intent(ctx, AoaProbeService::class.java)
if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
}
fun poll(ctx: Context) {
ctx.startService(Intent(ctx, AoaProbeService::class.java).setAction(ACTION_POLL))
}
}
}
