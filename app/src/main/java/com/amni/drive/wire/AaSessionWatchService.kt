package com.amni.drive.wire
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.amni.drive.config.ConfigStore
import com.amni.drive.config.DriveConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
class AaSessionWatchService : Service() {
private var receiver: BroadcastReceiver? = null
private val scope = CoroutineScope(Dispatchers.IO + Job())
private var tickJob: Job? = null
private var lastMode = DriveConfig.PATH_AUTO
override fun onBind(intent: Intent?): IBinder? = null
override fun onCreate() {
super.onCreate()
CarLink.start(this)
ensureChannel()
startForeground(NOTIF_ID, buildNotif("AA dual-path watch"))
val filter = IntentFilter().apply {
addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
addAction(AoaProbeService.ACTION_USB_STATE)
}
receiver = object : BroadcastReceiver() {
override fun onReceive(ctx: Context?, intent: Intent?) {
if (intent == null) return
when (intent.action) {
UsbManager.ACTION_USB_ACCESSORY_ATTACHED -> log("WATCH ATTACHED gearhead=${gearheadAlive()} mode=$lastMode")
UsbManager.ACTION_USB_ACCESSORY_DETACHED -> log("WATCH DETACHED gearhead=${gearheadAlive()} mode=$lastMode")
AoaProbeService.ACTION_USB_STATE -> {
val connected = intent.getBooleanExtra("connected", false)
val configured = intent.getBooleanExtra("configured", false)
val accessory = intent.getBooleanExtra("accessory", false)
log("WATCH USB connected=$connected configured=$configured accessory=$accessory gearhead=${gearheadAlive()} mode=$lastMode")
}
}
}
}
if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
else @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(receiver, filter)
tickJob = scope.launch {
while (true) {
val cfg = runCatching { ConfigStore(this@AaSessionWatchService).config.first() }.getOrNull()
lastMode = cfg?.aaPathMode ?: DriveConfig.PATH_RIDE
val gh = gearheadAlive()
val acc = accessoryPresent()
log("tick mode=$lastMode gearhead=$gh carConn=${CarLink.type.value} accessory=$acc (passive · no AOA claim)")
updateNotif("PASSIVE $lastMode · gh=$gh · car=${CarLink.type.value} · acc=$acc")
kotlinx.coroutines.delay(8_000L)
}
}
log("AaSessionWatch started passive (OWN claim is explicit only — protects wifi adb)")
}
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
when (intent?.action) {
ACTION_TICK -> scope.launch {
val cfg = runCatching { ConfigStore(this@AaSessionWatchService).config.first() }.getOrNull()
lastMode = cfg?.aaPathMode ?: DriveConfig.PATH_RIDE
log("forced tick mode=$lastMode gearhead=${gearheadAlive()} accessory=${accessoryPresent()} passive")
}
ACTION_CLAIM_OWN -> {
log("explicit OWN claim requested")
AoaProbeService.claim(this)
}
}
return START_STICKY
}
override fun onDestroy() {
tickJob?.cancel()
runCatching { unregisterReceiver(receiver) }
super.onDestroy()
}

@Suppress("DEPRECATION")
private fun gearheadAlive(): Boolean {
return CarLink.connected || try {
val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
am.runningAppProcesses?.any { it.processName.startsWith("com.google.android.projection.gearhead") } == true
} catch (_: Exception) {
false
}
}
@Suppress("DEPRECATION")
private fun gearheadCarSession(): Boolean {
return CarLink.projecting || try {
val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
am.runningAppProcesses?.any {
it.processName == "com.google.android.projection.gearhead:car" ||
it.processName == "com.google.android.projection.gearhead:projection"
} == true
} catch (_: Exception) {
false
}
}
private fun accessoryPresent(): Boolean {
return try {
val list = (getSystemService(USB_SERVICE) as UsbManager).accessoryList
list != null && list.isNotEmpty()
} catch (_: Exception) {
false
}
}
private fun log(msg: String) {
Log.i(TAG, msg)
val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
val line = "${SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())} $msg\n"
val bytes = line.toByteArray()
try {
val dir = File(filesDir, "aoa_probe")
if (!dir.exists()) dir.mkdirs()
FileOutputStream(File(dir, "watch-$day.log"), true).use { it.write(bytes) }
} catch (_: Exception) {}
try {
val ext = getExternalFilesDir(null) ?: return
val dir = File(ext, "aoa_probe")
if (!dir.exists()) dir.mkdirs()
FileOutputStream(File(dir, "watch-$day.log"), true).use { it.write(bytes) }
} catch (_: Exception) {}
}
private fun ensureChannel() {
if (Build.VERSION.SDK_INT < 26) return
getSystemService(NotificationManager::class.java)
.createNotificationChannel(NotificationChannel(CHANNEL, "Amni AA dual-path", NotificationManager.IMPORTANCE_LOW))
}
private fun buildNotif(text: String): Notification {
val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
return b.setContentTitle("Amni dual-path").setContentText(text)
.setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setOngoing(true).build()
}
private fun updateNotif(text: String) {
getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotif(text))
}
companion object {
const val TAG = "Amni.AaWatch"
const val CHANNEL = "amni_aa_watch"
const val NOTIF_ID = 7733
const val ACTION_TICK = "com.amni.drive.wire.WATCH_TICK"
const val ACTION_CLAIM_OWN = "com.amni.drive.wire.WATCH_CLAIM_OWN"
fun start(ctx: Context) {
val i = Intent(ctx, AaSessionWatchService::class.java)
if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
}
fun tick(ctx: Context) {
start(ctx)
ctx.startService(Intent(ctx, AaSessionWatchService::class.java).setAction(ACTION_TICK))
}
fun claimOwn(ctx: Context) {
start(ctx)
ctx.startService(Intent(ctx, AaSessionWatchService::class.java).setAction(ACTION_CLAIM_OWN))
}
}
}
