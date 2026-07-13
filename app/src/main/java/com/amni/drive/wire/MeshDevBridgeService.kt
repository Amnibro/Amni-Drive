package com.amni.drive.wire
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.amni.drive.MainActivity
import com.amni.drive.config.ConfigStore
import com.amni.drive.config.DriveConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.Charset
import java.security.SecureRandom
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
class MeshDevBridgeService : Service() {
private val running = AtomicBoolean(false)
private var serverThread: Thread? = null
private var server: ServerSocket? = null
override fun onBind(intent: Intent?): IBinder? = null
override fun onCreate() {
super.onCreate()
ensureChannel()
startForeground(NOTIF_ID, buildNotif("Mesh bridge :$PORT"))
startServer()
}
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
if (intent?.action == ACTION_STOP) {
stopSelf()
return START_NOT_STICKY
}
if (!running.get()) startServer()
return START_STICKY
}
override fun onDestroy() {
running.set(false)
runCatching { server?.close() }
serverThread = null
super.onDestroy()
}
private fun startServer() {
if (!running.compareAndSet(false, true)) return
serverThread = Thread({
try {
val ss = ServerSocket(PORT)
server = ss
log("MeshDevBridge listening 0.0.0.0:$PORT token=${token().take(8)}… ips=${localIps().joinToString()}")
updateNotif("Mesh :$PORT · ${localIps().firstOrNull() ?: "?"}")
while (running.get()) {
val sock = try { ss.accept() } catch (_: Exception) { break }
Thread({ handleClient(sock) }, "mesh-bridge-cli").also { it.isDaemon = true; it.start() }
}
} catch (e: Exception) {
log("server fail: ${e.message}")
running.set(false)
} finally {
runCatching { server?.close() }
}
}, "mesh-dev-bridge").also { it.isDaemon = true; it.start() }
}
private fun handleClient(sock: Socket) {
try {
sock.soTimeout = 15_000
val input = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
val reqLine = input.readLine() ?: return
val parts = reqLine.split(' ')
if (parts.size < 2) { writeText(sock, 400, "bad request"); return }
val method = parts[0]
val rawPath = parts[1]
while (true) {
val h = input.readLine() ?: break
if (h.isEmpty()) break
}
val q = parseQuery(rawPath)
val path = rawPath.substringBefore('?')
val tok = q["token"] ?: q["t"]
val publicGet = method == "GET" && (path == "/health" || path == "/token" || path == "/")
if (!publicGet && tok != token()) {
writeText(sock, 401, "unauthorized · pass ?token=…")
return
}
when {
method == "GET" && path == "/health" -> writeJson(sock, 200, """{"ok":true,"port":$PORT,"ver":"${appVersion()}","ips":${jsonArr(localIps())}}""")
method == "GET" && path == "/status" -> writeJson(sock, 200, statusJson())
method == "GET" && path == "/own" -> writeJson(sock, 200, ownJson())
method == "GET" && path == "/logs/watch" -> writeText(sock, 200, tailLog("watch"), "text/plain")
method == "GET" && path == "/logs/probe" -> writeText(sock, 200, tailLog("probe"), "text/plain")
method == "GET" && path == "/logcat" -> writeText(sock, 200, dumpLogcat(q["tag"] ?: "Amni"), "text/plain")
method == "GET" && path == "/token" -> writeText(sock, 200, token(), "text/plain")
method == "POST" && path == "/action/open-nav" -> {
runCatching { startActivity(MainActivity.openNavIntent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
writeJson(sock, 200, """{"ok":true,"action":"open-nav"}""")
}
method == "POST" && path == "/action/open-link" -> {
runCatching {
startActivity(Intent(this, com.amni.drive.LinkActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
writeJson(sock, 200, """{"ok":true,"action":"open-link"}""")
}
method == "POST" && path == "/action/mode" -> {
val m = DriveConfig.normalizePath(q["v"] ?: q["mode"])
runBlocking { ConfigStore(this@MeshDevBridgeService).setAaPathMode(m) }
writeJson(sock, 200, """{"ok":true,"mode":"$m"}""")
}
method == "POST" && path == "/action/claim-own" -> {
runBlocking { ConfigStore(this@MeshDevBridgeService).setAaPathMode(DriveConfig.PATH_OWN) }
AoaProbeService.claim(this)
writeJson(sock, 200, """{"ok":true,"action":"claim-own","warn":"may disrupt wifi adb","wall":"AuthComplete status=-3 until host identity"}""")
}
method == "POST" && path == "/action/watch-tick" -> {
AaSessionWatchService.tick(this)
writeJson(sock, 200, """{"ok":true,"action":"watch-tick"}""")
}
method == "GET" && path == "/" -> writeText(sock, 200, helpHtml(), "text/html")
else -> writeText(sock, 404, "not found: $path")
}
} catch (e: Exception) {
log("client err: ${e.message}")
} finally {
runCatching { sock.close() }
}
}
private fun statusJson(): String {
val mode = runCatching { runBlocking { ConfigStore(this@MeshDevBridgeService).config.first().aaPathMode } }.getOrDefault(DriveConfig.PATH_RIDE)
val gh = gearheadAlive()
val ghCar = gearheadCarSession()
val acc = accessoryPresent()
val ips = localIps()
return """{"ok":true,"mode":"$mode","gearhead":$gh,"gearheadCar":$ghCar,"accessory":$acc,"ver":"${appVersion()}","ips":${jsonArr(ips)},"port":$PORT,"adbSafe":${mode != DriveConfig.PATH_OWN},"ownWall":"AuthComplete-3","replacement":"link+ride"}"""
}
private fun ownJson(): String {
val mode = runCatching { runBlocking { ConfigStore(this@MeshDevBridgeService).config.first().aaPathMode } }.getOrDefault(DriveConfig.PATH_RIDE)
val gh = gearheadAlive()
val acc = accessoryPresent()
val wall = "AuthComplete status=-3 after TLS (factory host identity). No stolen Google certs."
val next = when {
gh -> "RIDE: gearhead alive · open Amni NAV surface (full UI paint)"
acc && !gh -> "OWN: accessory free · claim-own may open AOA (kills wifi adb risk)"
else -> "LINK: no accessory · Amni Link host/receiver is the open gearhead replacement"
}
return """{"ok":true,"mode":"$mode","gearhead":$gh,"accessory":$acc,"wall":"$wall","next":"$next","tracks":["ride-surface","own-wire-lab","amni-link"],"docs":["docs/research/11_dual_path_ride_and_own.md","docs/research/13_auth_complete_status_minus3_cert_wall.md","docs/research/14_gearhead_replacement_tracks.md"],"ver":"${appVersion()}"}"""
}
private fun helpHtml(): String = """
<html><body style="font-family:monospace;background:#0a0b0e;color:#0f9;padding:16px">
<h2>Amni Mesh Dev Bridge</h2>
<p>Auth: ?token=TOKEN on every request</p>
<ul>
<li>GET /health /status /own /logs/watch /logs/probe /logcat?tag=Amni</li>
<li>POST /action/open-nav /action/open-link /action/mode?v=ride|own|auto /action/claim-own /action/watch-tick</li>
</ul>
<p>RIDE = gearhead holds certs · Amni paints surface. OWN = wire host (Auth -3 wall). LINK = open AA replacement for Android HUs.</p>
</body></html>
""".trimIndent()
private fun tailLog(kind: String): String {
val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
val names = listOf("filesDir" to File(filesDir, "aoa_probe/$kind-$day.log"), "ext" to File(getExternalFilesDir(null), "aoa_probe/$kind-$day.log"))
val f = names.map { it.second }.firstOrNull { it.exists() } ?: return "(no $kind log yet)"
return try { f.readText().takeLast(48_000) } catch (e: Exception) { "err ${e.message}" }
}
private fun dumpLogcat(tag: String): String {
return try {
val p = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "250", "*:S", "$tag:V", "Amni.AaWatch:V", "Amni.AoaProbe:V", "Amni.MeshBridge:V"))
val out = p.inputStream.bufferedReader().readText()
p.waitFor()
out.ifBlank { "(empty logcat)" }
} catch (e: Exception) { "logcat err: ${e.message}" }
}
private fun gearheadProcs(): List<String> = try {
val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", "ps -A 2>/dev/null | grep projection.gearhead"))
p.inputStream.bufferedReader().use { it.readLines() }.also { runCatching { p.waitFor() } }
} catch (_: Exception) { emptyList() }
private fun gearheadAlive(): Boolean = gearheadProcs().any { it.contains("projection.gearhead") }
private fun gearheadCarSession(): Boolean = gearheadProcs().any {
it.contains("gearhead:car") || it.contains("gearhead:projection")
}
private fun accessoryPresent(): Boolean = try {
val list = (getSystemService(USB_SERVICE) as UsbManager).accessoryList
list != null && list.isNotEmpty()
} catch (_: Exception) { false }
private fun appVersion(): String = try {
val pi = if (Build.VERSION.SDK_INT >= 33) packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
else @Suppress("DEPRECATION") packageManager.getPackageInfo(packageName, 0)
"${pi.versionName} (${if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode})"
} catch (_: Exception) { "?" }
private fun localIps(): List<String> {
val out = ArrayList<String>()
try {
for (ni in Collections.list(NetworkInterface.getNetworkInterfaces())) {
if (!ni.isUp || ni.isLoopback) continue
for (a in Collections.list(ni.inetAddresses)) {
if (a is Inet4Address && !a.isLoopbackAddress) out.add(a.hostAddress ?: continue)
}
}
} catch (_: Exception) {}
return out.distinct()
}
private fun token(): String {
val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
var t = prefs.getString(KEY_TOKEN, null)
if (t.isNullOrBlank()) {
val bytes = ByteArray(16)
SecureRandom().nextBytes(bytes)
t = bytes.joinToString("") { "%02x".format(it) }
prefs.edit().putString(KEY_TOKEN, t).apply()
}
return t!!
}
private fun parseQuery(raw: String): Map<String, String> {
val q = raw.substringAfter('?', "")
if (q.isEmpty()) return emptyMap()
return q.split('&').mapNotNull {
val i = it.indexOf('=')
if (i < 0) null
else URLDecoder.decode(it.substring(0, i), "UTF-8") to URLDecoder.decode(it.substring(i + 1), "UTF-8")
}.toMap()
}
private fun jsonArr(list: List<String>): String =
list.joinToString(prefix = "[", postfix = "]") { "\"${it.replace("\"", "")}\"" }
private fun writeJson(sock: Socket, code: Int, body: String) = writeText(sock, code, body, "application/json")
private fun writeText(sock: Socket, code: Int, body: String, type: String = "text/plain") {
val bytes = body.toByteArray(Charsets.UTF_8)
val reason = when (code) { 200 -> "OK"; 401 -> "Unauthorized"; 404 -> "Not Found"; else -> "Err" }
val head = "HTTP/1.1 $code $reason\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\nAccess-Control-Allow-Origin: *\r\n\r\n"
val os: OutputStream = sock.getOutputStream()
os.write(head.toByteArray(Charsets.US_ASCII))
os.write(bytes)
os.flush()
}
private fun log(msg: String) { Log.i(TAG, msg) }
private fun ensureChannel() {
if (Build.VERSION.SDK_INT < 26) return
getSystemService(NotificationManager::class.java)
.createNotificationChannel(NotificationChannel(CHANNEL, "Amni Mesh Dev Bridge", NotificationManager.IMPORTANCE_LOW))
}
private fun buildNotif(text: String): Notification {
val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
return b.setContentTitle("Amni Mesh Bridge").setContentText(text)
.setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setOngoing(true).build()
}
private fun updateNotif(text: String) {
getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotif(text))
}
companion object {
const val TAG = "Amni.MeshBridge"
const val CHANNEL = "amni_mesh_bridge"
const val NOTIF_ID = 7740
const val PORT = 7931
const val PREFS = "amni_mesh_bridge"
const val KEY_TOKEN = "token"
const val ACTION_STOP = "com.amni.drive.wire.MESH_STOP"
fun start(ctx: Context) {
val i = Intent(ctx, MeshDevBridgeService::class.java)
if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
}
fun stop(ctx: Context) {
ctx.startService(Intent(ctx, MeshDevBridgeService::class.java).setAction(ACTION_STOP))
ctx.stopService(Intent(ctx, MeshDevBridgeService::class.java))
}
fun token(ctx: Context): String {
val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
var t = prefs.getString(KEY_TOKEN, null)
if (t.isNullOrBlank()) {
val bytes = ByteArray(16)
SecureRandom().nextBytes(bytes)
t = bytes.joinToString("") { "%02x".format(it) }
prefs.edit().putString(KEY_TOKEN, t).apply()
}
return t!!
}
}
}
