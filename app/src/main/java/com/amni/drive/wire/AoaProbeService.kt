package com.amni.drive.wire
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
class AoaProbeService : Service() {
private var receiver: BroadcastReceiver? = null
private val accessoryFd = AtomicReference<ParcelFileDescriptor?>(null)
private var sniffThread: Thread? = null
override fun onBind(intent: Intent?): IBinder? = null
override fun onCreate() {
super.onCreate()
ensureChannel()
startForeground(NOTIF_ID, buildNotif("AOA probe idle"))
val filter = IntentFilter().apply {
addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
addAction(ACTION_USB_STATE)
addAction(ACTION_USB_PERMISSION)
}
receiver = object : BroadcastReceiver() {
override fun onReceive(ctx: Context?, intent: Intent?) {
if (intent == null) return
when (intent.action) {
UsbManager.ACTION_USB_ACCESSORY_ATTACHED -> {
val acc = readAccessory(intent)
log("ATTACHED ${desc(acc)}")
requestOrOpen(acc)
}
UsbManager.ACTION_USB_ACCESSORY_DETACHED -> {
log("DETACHED ${desc(readAccessory(intent))}")
closeAccessory()
updateNotif("AOA detached")
}
ACTION_USB_STATE -> {
val connected = intent.getBooleanExtra("connected", false)
val configured = intent.getBooleanExtra("configured", false)
val accessory = intent.getBooleanExtra("accessory", false)
log("USB_STATE connected=$connected configured=$configured accessory=$accessory")
if (accessory) pollAccessories(requestPerm = true)
}
ACTION_USB_PERMISSION -> {
val acc = readAccessory(intent)
val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
log("PERMISSION granted=$granted ${desc(acc)}")
if (granted) {
updateNotif("AOA permission OK · opening")
tryOpen(acc)
if (accessoryFd.get() == null) {
log("open failed after grant — retrying poll")
pollAccessories(requestPerm = false)
}
} else updateNotif("AOA permission denied")
}
}
}
}
if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
else @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(receiver, filter)
pollAccessories(requestPerm = true)
log("probe service started")
}
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
when (intent?.action) {
ACTION_POLL -> pollAccessories(requestPerm = true)
ACTION_CLAIM -> {
log("CLAIM requested")
pollAccessories(requestPerm = true)
}
}
return START_STICKY
}
override fun onDestroy() {
runCatching { unregisterReceiver(receiver) }
closeAccessory()
super.onDestroy()
}
private fun pollAccessories(requestPerm: Boolean) {
val usb = getSystemService(USB_SERVICE) as UsbManager
val list = usb.accessoryList
if (list.isNullOrEmpty()) {
log("poll: no accessories")
updateNotif("AOA probe · no accessory")
return
}
list.forEach { acc ->
val has = try { usb.hasPermission(acc) } catch (_: Exception) { false }
log("poll: ${desc(acc)} perm=$has")
if (has) {
updateNotif("AOA perm OK · opening")
tryOpen(acc)
} else if (requestPerm) requestOrOpen(acc)
}
}
private fun requestOrOpen(acc: UsbAccessory?) {
if (acc == null) return
val usb = getSystemService(USB_SERVICE) as UsbManager
if (usb.hasPermission(acc)) {
tryOpen(acc)
return
}
log("requesting USB accessory permission…")
updateNotif("AOA · requesting permission")
val flags = PendingIntent.FLAG_UPDATE_CURRENT or
if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
val pi = PendingIntent.getBroadcast(
this, 0,
Intent(ACTION_USB_PERMISSION).setPackage(packageName),
flags
)
try {
usb.requestPermission(acc, pi)
} catch (e: Exception) {
log("requestPermission error: ${e.message}")
}
}
private fun tryOpen(acc: UsbAccessory?) {
if (acc == null) return
val usb = getSystemService(USB_SERVICE) as UsbManager
if (!usb.hasPermission(acc)) {
log("no permission for ${desc(acc)}")
updateNotif("AOA seen · no permission")
return
}
closeAccessory()
try {
val pfd = usb.openAccessory(acc)
if (pfd == null) {
log("openAccessory null (gearhead may hold FD) ${desc(acc)}")
updateNotif("AOA open null · gearhead holds?")
return
}
accessoryFd.set(pfd)
log("OPENED fd=${pfd.fd} ${desc(acc)}")
updateNotif("AOA OPEN · TLS attempt")
sniffThread = Thread({ sessionAfterOpen(pfd) }, "aoa-session").also { it.isDaemon = true; it.start() }
} catch (e: Exception) {
log("open error: ${e.message}")
updateNotif("AOA error ${e.message}")
}
}
private fun sessionAfterOpen(pfd: ParcelFileDescriptor) {
try {
val fis = FileInputStream(pfd.fileDescriptor)
val fos = FileOutputStream(pfd.fileDescriptor)
val dump = File(filesDir, "aoa_probe/first_chunk.bin")
dump.parentFile?.mkdirs()
// Phase A: brief passive sniff (some HU send version before TLS)
val buf = ByteArray(4096)
var total = 0
val t0 = System.currentTimeMillis()
log("phase A passive sniff 2s")
FileOutputStream(dump).use { out ->
while (accessoryFd.get() != null && System.currentTimeMillis() - t0 < 2_000L) {
val avail = try { fis.available() } catch (_: Exception) { 0 }
if (avail <= 0) { Thread.sleep(30); continue }
val n = try { fis.read(buf) } catch (_: Exception) { -1 }
if (n <= 0) break
total += n
out.write(buf, 0, n)
log("RX $n head=${buf.take(n.coerceAtMost(32)).joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }}")
}
}
log("phase A total=$total — starting TLS server attempt")
updateNotif("AOA · TLS handshake…")
// Phase B: TLS server (research self-signed; expect cert fail on real HU)
try {
val ssl = AoaSslSession.fromFd(fis, fos) { log(it) }
val ok = ssl.handshake(12_000L)
log("TLS result ok=$ok")
updateNotif(if (ok) "AOA TLS OK (research)" else "AOA TLS fail/timeout (expected w/ self-signed)")
} catch (e: Exception) {
log("TLS error ${e.message}")
updateNotif("AOA TLS error ${e.message}")
}
// Phase C: hold + continue sniff residual
val t1 = System.currentTimeMillis()
while (accessoryFd.get() != null && System.currentTimeMillis() - t1 < 10_000L) {
val avail = try { fis.available() } catch (_: Exception) { 0 }
if (avail > 0) {
val n = try { fis.read(buf) } catch (_: Exception) { -1 }
if (n > 0) {
total += n
log("post-TLS RX $n total=$total")
}
} else Thread.sleep(50)
}
log("session end totalRX=$total")
try { fos.close() } catch (_: Exception) {}
try { fis.close() } catch (_: Exception) {}
} catch (e: Exception) {
log("session error ${e.message}")
}
}
private fun closeAccessory() {
sniffThread = null
val fd = accessoryFd.getAndSet(null)
try { fd?.close() } catch (_: Exception) {}
}
private fun readAccessory(intent: Intent): UsbAccessory? =
if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
else @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
private fun desc(acc: UsbAccessory?): String {
if (acc == null) return "null"
val sn = try { acc.serial } catch (_: SecurityException) { "(no-perm)" }
return "mfr=${acc.manufacturer} model=${acc.model} ver=${acc.version} desc=${acc.description} uri=${acc.uri} sn=$sn"
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
const val ACTION_CLAIM = "com.amni.drive.wire.CLAIM_AOA"
const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"
const val ACTION_USB_PERMISSION = "com.amni.drive.wire.USB_PERMISSION"
fun start(ctx: Context) {
val i = Intent(ctx, AoaProbeService::class.java)
if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
}
fun poll(ctx: Context) {
ctx.startService(Intent(ctx, AoaProbeService::class.java).setAction(ACTION_POLL))
}
fun claim(ctx: Context) {
start(ctx)
ctx.startService(Intent(ctx, AoaProbeService::class.java).setAction(ACTION_CLAIM))
}
}
}
