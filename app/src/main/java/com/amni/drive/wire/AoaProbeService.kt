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
import com.amni.drive.config.ConfigStore
import com.amni.drive.config.DriveConfig
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
class AoaProbeService : Service() {
private var receiver: BroadcastReceiver? = null
private val accessoryFd = AtomicReference<ParcelFileDescriptor?>(null)
private var sniffThread: Thread? = null
private var freePollThread: Thread? = null
private val freePollActive = AtomicReference(false)
private val sessionRunning = AtomicReference(false)
private val usbIo = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "aoa-usb-io").apply { isDaemon = true } }
private val svcScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
@Volatile private var cachedMode: String = DriveConfig.PATH_AUTO
override fun onBind(intent: Intent?): IBinder? = null
private fun shouldClaimUsb(mode: String = cachedMode): Boolean =
mode == DriveConfig.PATH_OWN || (mode == DriveConfig.PATH_AUTO && !gearheadCarSessionAlive())
override fun onCreate() {
super.onCreate()
ensureChannel()
startForeground(NOTIF_ID, buildNotif("AOA probe idle"))
val store = (application as? com.amni.drive.AmniDriveApp)?.configStore ?: ConfigStore(this)
svcScope.launch {
runCatching {
store.config.collect { c -> cachedMode = c.aaPathMode }
}
}
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
usbIo.execute {
if (shouldClaimUsb()) requestOrOpen(acc)
else log("ATTACHED ignored · mode=${pathMode()} (protect wifi adb)")
}
}
UsbManager.ACTION_USB_ACCESSORY_DETACHED -> {
val acc = readAccessory(intent)
log("DETACHED ${desc(acc)}")
usbIo.execute {
closeAccessory()
updateNotif("AOA detached")
if (shouldClaimUsb()) scheduleFreePoll()
}
}
ACTION_USB_STATE -> {
val connected = intent.getBooleanExtra("connected", false)
val configured = intent.getBooleanExtra("configured", false)
val accessory = intent.getBooleanExtra("accessory", false)
log("USB_STATE connected=$connected configured=$configured accessory=$accessory mode=${pathMode()}")
if (accessory) {
usbIo.execute {
if (shouldClaimUsb()) {
pollAccessories(requestPerm = true)
if (accessoryFd.get() == null) scheduleFreePoll()
}
}
}
}
ACTION_USB_PERMISSION -> {
val acc = readAccessory(intent)
val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
log("PERMISSION granted=$granted ${desc(acc)}")
usbIo.execute {
if (granted && shouldClaimUsb()) {
updateNotif("AOA permission OK · opening")
tryOpen(acc)
if (accessoryFd.get() == null) scheduleFreePoll()
} else if (!granted) updateNotif("AOA permission denied")
else log("PERMISSION ignored · mode=${pathMode()}")
}
}
}
}
}
if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
else @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(receiver, filter)
usbIo.execute {
if (shouldClaimUsb()) {
pollAccessories(requestPerm = true)
if (accessoryFd.get() == null) scheduleFreePoll()
}
}
log("probe service started mode=${pathMode()} gearhead=${gearheadProjectionAlive()}")
}
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
when (intent?.action) {
ACTION_POLL -> {
usbIo.execute {
if (shouldClaimUsb()) {
pollAccessories(requestPerm = true)
if (accessoryFd.get() == null) scheduleFreePoll()
} else log("POLL ignored · mode=${pathMode()}")
}
}
ACTION_CLAIM -> {
log("CLAIM → force OWN session open gearhead=${gearheadProjectionAlive()}")
cachedMode = DriveConfig.PATH_OWN
val store = (application as? com.amni.drive.AmniDriveApp)?.configStore ?: ConfigStore(this)
svcScope.launch { runCatching { store.setAaPathMode(DriveConfig.PATH_OWN) } }
usbIo.execute {
if (accessoryFd.get() != null && sessionRunning.get()) {
log("CLAIM skipped — active OWN session already running")
return@execute
}
freePollActive.set(false)
sessionRunning.set(false)
closeAccessory()
log("CLAIM reset FD/session for fresh open")
pollAccessories(requestPerm = true)
if (accessoryFd.get() == null) scheduleFreePoll()
}
}
else -> { }
}
return START_STICKY
}
override fun onDestroy() {
freePollActive.set(false)
runCatching { unregisterReceiver(receiver) }
svcScope.cancel()
usbIo.execute { closeAccessory() }
super.onDestroy()
}
private fun pollAccessories(requestPerm: Boolean) {
val usb = getSystemService(USB_SERVICE) as UsbManager
val list = usb.accessoryList
if (list.isNullOrEmpty()) {
log("poll: no accessories gearhead=${gearheadProjectionAlive()}")
updateNotif("AOA probe · no accessory")
return
}
list.forEach { acc ->
val has = try { usb.hasPermission(acc) } catch (_: Exception) { false }
log("poll: ${desc(acc)} perm=$has gearhead=${gearheadProjectionAlive()}")
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
private fun pathMode(): String = cachedMode
private val openLock = Any()

private fun tryOpen(acc: UsbAccessory?) {
if (acc == null) return
synchronized(openLock) {
val mode = pathMode()
val ghCar = gearheadCarSessionAlive()
if (mode == DriveConfig.PATH_RIDE) {
log("RIDE mode · refuse openAccessory gearheadCar=$ghCar ${desc(acc)}")
updateNotif("RIDE · Gearhead owns session")
return
}
if (mode == DriveConfig.PATH_AUTO && ghCar) {
log("AUTO · gearhead:car session · Path A hold (no FD steal) ${desc(acc)}")
updateNotif("AUTO→RIDE · gearhead:car")
scheduleFreePoll()
return
}
val usb = getSystemService(USB_SERVICE) as UsbManager
if (!usb.hasPermission(acc)) {
log("no permission for ${desc(acc)}")
updateNotif("AOA seen · no permission")
return
}
if (accessoryFd.get() != null || sessionRunning.get()) {
log("already holding FD/session — skip re-open")
return
}
try {
val pfd = usb.openAccessory(acc)
if (pfd == null) {
log("openAccessory null dual-path wait free gearheadCar=$ghCar mode=$mode ${desc(acc)}")
updateNotif(if (ghCar) "AOA busy · gearhead holds FD" else "AOA open null · retry later")
scheduleFreePoll()
return
}
accessoryFd.set(pfd)
freePollActive.set(false)
log("OPENED fd=${pfd.fd} mode=$mode gearheadCar=${gearheadCarSessionAlive()} ${desc(acc)}")
updateNotif("OWN · AOA OPEN · TLS")
sniffThread = Thread({ sessionAfterOpen(pfd) }, "aoa-session").also { it.isDaemon = true; it.start() }
} catch (e: Exception) {
log("open error: ${e.message}")
updateNotif("AOA error ${e.message}")
scheduleFreePoll()
}
}
}
private fun gearheadProjectionAlive(): Boolean {
return CarLink.connected || try {
val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
@Suppress("DEPRECATION")
am.runningAppProcesses?.any {
it.processName.startsWith("com.google.android.projection.gearhead")
} == true
} catch (_: Exception) {
false
}
}
private fun gearheadCarSessionAlive(): Boolean {
return CarLink.projecting || try {
val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
@Suppress("DEPRECATION")
am.runningAppProcesses?.any {
it.processName == "com.google.android.projection.gearhead:car" ||
it.processName == "com.google.android.projection.gearhead:projection"
} == true
} catch (_: Exception) {
false
}
}
private fun scheduleFreePoll() {
if (!freePollActive.compareAndSet(false, true)) return
log("dual-path free-poll armed sticky (non-destructive, no gearhead kill)")
freePollThread = Thread({
var n = 0
while (freePollActive.get() && accessoryFd.get() == null) {
Thread.sleep(if (n < 20) 2_000L else 5_000L)
n++
if (!freePollActive.get() || accessoryFd.get() != null) break
val gh = gearheadProjectionAlive()
if (n % 5 == 0 || n <= 3) log("free-poll #$n gearhead=$gh")
pollAccessories(requestPerm = !gh)
if (gh) updateNotif("AOA wait · gearhead busy ($n)")
else updateNotif("AOA free-poll #$n")
}
freePollActive.set(false)
log("dual-path free-poll end held=${accessoryFd.get() != null}")
}, "aoa-free-poll").also { it.isDaemon = true; it.start() }
}
private fun sessionAfterOpen(pfd: ParcelFileDescriptor) {
if (!sessionRunning.compareAndSet(false, true)) {
log("session already running — skip re-open (exclusive FD)")
try { pfd.close() } catch (_: Exception) {}
return
}
val io = AoaBulkIo(pfd) { log(it) }
var total = 0
var aapOk = false
var tlsOk = false
var postOk = false
var videoChannel = AaPostTls.CH_VIDEO
try {
val dump = File(filesDir, "aoa_probe/first_chunk.bin")
dump.parentFile?.mkdirs()
val seed = java.io.ByteArrayOutputStream()
var repliedVersion = false
var sslSeed = emptyList<ByteArray>()
log("phase A active sniff ≤1.2s (reply VersionRequest immediately)")
val t0 = System.currentTimeMillis()
FileOutputStream(dump).use { out ->
while (accessoryFd.get() != null && System.currentTimeMillis() - t0 < 1_200L) {
val chunk = io.waitDrain(80)
if (chunk.isEmpty()) continue
total += chunk.size
out.write(chunk)
seed.write(chunk)
log("phase A bulk got ${chunk.size} total=$total")
val frames = AaAapVersion.scanFrames(seed.toByteArray(), { log(it) }, "phaseA")
val req = frames.firstOrNull { it.msgId == AaAapVersion.MSG_VERSION_REQUEST }
if (req != null && !repliedVersion) {
val maj = if (req.payload.size >= 2) ((req.payload[0].toInt() and 0xFF) shl 8) or (req.payload[1].toInt() and 0xFF) else 1
val min = if (req.payload.size >= 4) ((req.payload[2].toInt() and 0xFF) shl 8) or (req.payload[3].toInt() and 0xFF) else 1
val rsp = AaAapVersion.versionResponse(maj, min, AaAapVersion.STATUS_OK)
io.write(rsp)
repliedVersion = true
aapOk = true
log("phase A TX VersionResponse echo=$maj.$min ${rsp.size}B hex=${AaAapVersion.hexHead(rsp)}")
seed.reset()
}
val ssl = AaAapVersion.sslBodiesOf(frames)
if (ssl.isNotEmpty()) {
sslSeed = ssl
log("phase A got SSL_HANDSHAKE bodies=${ssl.size}")
if (repliedVersion) {
log("phase A jump TLS with seed")
break
}
}
if (repliedVersion && System.currentTimeMillis() - t0 > 600L && sslSeed.isNotEmpty()) break
if (repliedVersion && System.currentTimeMillis() - t0 > 1_000L) break
}
}
log("phase A total=$total repliedVersion=$repliedVersion sslSeed=${sslSeed.size}")
updateNotif("AOA · AAP bulk")
try {
if (repliedVersion) {
if (sslSeed.isEmpty()) {
log("AAP version answered — wait ClientHello")
val wait = java.io.ByteArrayOutputStream()
val tw = System.currentTimeMillis()
while (System.currentTimeMillis() - tw < 2_500L) {
val c = io.waitDrain(80)
if (c.isNotEmpty()) {
total += c.size
wait.write(c)
log("AAP post-version RX ${c.size}")
}
val fr = AaAapVersion.scanFrames(wait.toByteArray(), { log(it) }, "AAP")
sslSeed = AaAapVersion.sslBodiesOf(fr)
if (sslSeed.isNotEmpty()) break
}
}
aapOk = true
log("AAP post-version sslBodies=${sslSeed.size}")
} else {
val neg = AaAapVersion.negotiateBulk(io, { log(it) }, 12_000L, seed.toByteArray())
aapOk = neg.ok
if (sslSeed.isEmpty()) sslSeed = neg.sslBodies
log("AAP negotiate bulk result=$aapOk sslBodies=${sslSeed.size}")
}
} catch (e: Exception) {
log("AAP negotiate bulk error ${e.message}")
}
updateNotif("AOA · TLS framed bulk")
var ssl: AoaSslSession? = null
try {
ssl = AoaSslSession.fromBulk(this, io) { log(it) }
tlsOk = ssl.handshakeFramedBulk(io, 12_000L, sslSeed)
log("TLS framed bulk ok=$tlsOk aap=$aapOk")
if (!tlsOk) log("TLS framed bulk fail — raw path skipped (reader owns FD)")
updateNotif(if (tlsOk) "AOA TLS OK · post-TLS" else "AOA TLS fail · project anyway")
} catch (e: Exception) {
log("TLS bulk error ${e.message}")
updateNotif("AOA TLS error · project anyway")
}
if (tlsOk && ssl != null) {
updateNotif("AOA · Amni-Drive Live")
try {
val post = AaPostTls.runLiveSession(io, ssl, { log(it) }, 3_600_000L) { spec ->
val eng = AmniProjectionEngine(
context = applicationContext,
width = spec.width,
height = spec.height,
dpi = spec.dpi,
fps = spec.fps,
widthMargin = spec.widthMargin,
heightMargin = spec.heightMargin
)
eng.onLog = { log("proj $it") }
ProjectionHostService.active = eng
eng
}
postOk = post.discovery || post.rxFrames > 0
videoChannel = post.videoChannel
if (ProjectionHostService.active != null) ProjectionHostService.active = null
log("POST-TLS session end discovery=${post.discovery} videoCh=${post.videoChannel} rx=${post.rxFrames} notes=${post.notes}")
updateNotif("AOA session end · rx=${post.rxFrames}")
} catch (e: Exception) {
log("POST-TLS error ${e.message}")
}
}
log("session end totalRX=$total tls=$tlsOk aap=$aapOk post=$postOk bulkAlive=${io.isAlive()}")
} catch (e: Exception) {
log("session error ${e.message}")
} finally {
sessionRunning.set(false)
io.close()
if (accessoryFd.compareAndSet(pfd, null)) {
sniffThread = null
try { pfd.close() } catch (_: Exception) {}
} else {
try { pfd.close() } catch (_: Exception) {}
}
scheduleFreePoll()
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
val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
val line = "${SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())} $msg\n"
val bytes = line.toByteArray()
try {
val dir = File(filesDir, "aoa_probe")
if (!dir.exists()) dir.mkdirs()
FileOutputStream(File(dir, "probe-$day.log"), true).use { it.write(bytes) }
} catch (_: Exception) {}
try {
val ext = getExternalFilesDir(null) ?: return
val dir = File(ext, "aoa_probe")
if (!dir.exists()) dir.mkdirs()
FileOutputStream(File(dir, "probe-$day.log"), true).use { it.write(bytes) }
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
