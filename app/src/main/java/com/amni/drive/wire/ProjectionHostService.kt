package com.amni.drive.wire
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
/**
 * Foreground host for own-stack full-UI projection (VirtualDisplay + H.264).
 * Independent of Google Android Auto app allowlists.
 */
class ProjectionHostService : Service() {
private var engine: AmniProjectionEngine? = null
override fun onBind(intent: Intent?): IBinder? = null
override fun onCreate() {
super.onCreate()
ensureChannel()
startForeground(NOTIF_ID, notif("Projection idle"))
}
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
when (intent?.action) {
ACTION_STOP -> {
engine?.stop()
engine = null
update("Projection stopped")
stopSelf()
}
else -> startProjection()
}
return START_STICKY
}
private fun startProjection() {
if (engine?.isRunning() == true) {
update("Projection already live")
return
}
val eng = AmniProjectionEngine(applicationContext)
eng.onLog = { Log.i(TAG, it); appendLog(it) }
if (eng.start()) {
engine = eng
update("FULL UI projecting · VirtualDisplay + H.264")
active = eng
} else {
update("Projection failed — see logs")
stopSelf()
}
}
override fun onDestroy() {
engine?.stop()
if (active === engine) active = null
engine = null
super.onDestroy()
}
private fun ensureChannel() {
if (Build.VERSION.SDK_INT < 26) return
val nm = getSystemService(NotificationManager::class.java)
nm.createNotificationChannel(NotificationChannel(CHANNEL, "Amni Projection", NotificationManager.IMPORTANCE_LOW))
}
private fun notif(text: String): Notification {
val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL)
else @Suppress("DEPRECATION") Notification.Builder(this)
return b.setContentTitle("Amni Projection")
.setContentText(text)
.setSmallIcon(android.R.drawable.ic_menu_compass)
.setOngoing(true)
.build()
}
private fun update(text: String) {
getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notif(text))
}
private fun appendLog(msg: String) {
try {
val f = java.io.File(filesDir, "aoa_probe/projection.log")
f.parentFile?.mkdirs()
java.io.FileOutputStream(f, true).use {
it.write("${System.currentTimeMillis()} $msg\n".toByteArray())
}
} catch (_: Exception) {}
}
companion object {
const val TAG = "Amni.ProjectionHost"
const val CHANNEL = "amni_projection"
const val NOTIF_ID = 7733
const val ACTION_STOP = "com.amni.drive.wire.STOP_PROJECTION"
@Volatile var active: AmniProjectionEngine? = null
fun start(ctx: Context) {
val i = Intent(ctx, ProjectionHostService::class.java)
if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
}
fun stop(ctx: Context) {
ctx.startService(Intent(ctx, ProjectionHostService::class.java).setAction(ACTION_STOP))
}
}
}
