package com.amni.drive.link
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
class LinkHost(
private val width: Int = 1920,
private val height: Int = 720,
private val fps: Int = 12,
private val quality: Int = 55
) {
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val running = AtomicBoolean(false)
private val control = AtomicReference<Socket?>(null)
private val video = AtomicReference<Socket?>(null)
private var ctrlServer: ServerSocket? = null
private var videoServer: ServerSocket? = null
private var acceptJob: Job? = null
private var videoAcceptJob: Job? = null
private var pumpJob: Job? = null
private var readJob: Job? = null
private val main = Handler(Looper.getMainLooper())
var status: (String) -> Unit = {}
var onClient: (Boolean) -> Unit = {}
fun start(captureRoot: () -> View?) {
if (!running.compareAndSet(false, true)) return
acceptJob = scope.launch {
try {
ctrlServer = ServerSocket(LinkProto.PORT).also { it.reuseAddress = true }
status("HOST · control :${LinkProto.PORT} · video :${LinkProto.VIDEO_PORT}")
while (isActive && running.get()) {
val s = try { ctrlServer?.accept() } catch (_: Exception) { null } ?: break
control.getAndSet(s)?.closeQuiet()
status("HOST · control ${s.inetAddress.hostAddress}")
onClient(true)
startControlSession(s, captureRoot)
}
} catch (e: Exception) { status("HOST · control error ${e.message}") }
}
videoAcceptJob = scope.launch {
try {
videoServer = ServerSocket(LinkProto.VIDEO_PORT).also { it.reuseAddress = true }
while (isActive && running.get()) {
val s = try { videoServer?.accept() } catch (_: Exception) { null } ?: break
video.getAndSet(s)?.closeQuiet()
status("HOST · video channel ${s.inetAddress.hostAddress}")
}
} catch (_: Exception) {}
}
}
private fun startControlSession(s: Socket, captureRoot: () -> View?) {
readJob?.cancel(); pumpJob?.cancel()
readJob = scope.launch {
try {
LinkProto.write(s.getOutputStream(), LinkProto.HELLO_ACK, LinkProto.helloPayload("HOST", "AmniDrive"))
LinkProto.write(s.getOutputStream(), LinkProto.VIDEO_CFG, LinkProto.videoCfg(width, height, fps, quality))
while (isActive && running.get() && !s.isClosed) {
val msg = LinkProto.read(s.getInputStream()) ?: break
when (msg.first) {
LinkProto.TOUCH -> injectTouch(captureRoot(), msg.second)
LinkProto.PING -> LinkProto.write(s.getOutputStream(), LinkProto.PONG)
LinkProto.BYE -> break
LinkProto.HELLO -> LinkProto.write(s.getOutputStream(), LinkProto.HELLO_ACK, LinkProto.helloPayload("HOST", "AmniDrive"))
else -> {}
}
}
} catch (_: Exception) {
} finally {
s.closeQuiet(); control.compareAndSet(s, null); onClient(false); status("HOST · control gone · waiting")
}
}
pumpJob = scope.launch {
val interval = (1000L / fps.coerceIn(4, 30))
val jpeg = ByteArrayOutputStream(width * height / 4)
while (isActive && running.get() && control.get() === s && !s.isClosed) {
val view = withContext(Dispatchers.Main) { captureRoot() }
if (view != null && view.width > 0 && view.height > 0) {
val bmp = withContext(Dispatchers.Main) { grab(view, width, height) }
if (bmp != null) {
jpeg.reset()
bmp.compress(Bitmap.CompressFormat.JPEG, quality, jpeg)
bmp.recycle()
val bytes = jpeg.toByteArray()
val sink = video.get()?.takeIf { !it.isClosed } ?: s
try { LinkProto.write(sink.getOutputStream(), LinkProto.FRAME, bytes) } catch (_: Exception) {
if (sink !== s) video.set(null)
else break
}
}
}
delay(interval)
}
}
}
private fun grab(view: View, tw: Int, th: Int): Bitmap? {
return try {
val src = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
val c = Canvas(src)
view.draw(c)
val scaled = Bitmap.createScaledBitmap(src, tw, th, true)
if (scaled !== src) src.recycle()
scaled
} catch (_: Exception) { null }
}
private fun injectTouch(view: View?, payload: ByteArray) {
if (view == null) return
val t = LinkProto.parseTouch(payload)
val action = t[0].toInt()
val x = t[1].coerceIn(0f, 1f) * view.width
val y = t[2].coerceIn(0f, 1f) * view.height
val meAction = when (action) {
LinkProto.TOUCH_DOWN -> MotionEvent.ACTION_DOWN
LinkProto.TOUCH_MOVE -> MotionEvent.ACTION_MOVE
LinkProto.TOUCH_UP -> MotionEvent.ACTION_UP
else -> MotionEvent.ACTION_CANCEL
}
val now = SystemClock.uptimeMillis()
main.post {
val ev = MotionEvent.obtain(now, now, meAction, x, y, 0)
view.dispatchTouchEvent(ev)
ev.recycle()
}
}
fun stop() {
running.set(false)
acceptJob?.cancel(); videoAcceptJob?.cancel(); readJob?.cancel(); pumpJob?.cancel()
control.getAndSet(null)?.closeQuiet()
video.getAndSet(null)?.closeQuiet()
try { ctrlServer?.close() } catch (_: Exception) {}
try { videoServer?.close() } catch (_: Exception) {}
ctrlServer = null; videoServer = null
scope.cancel()
status("HOST · stopped")
onClient(false)
}
private fun Socket.closeQuiet() = try { close() } catch (_: Exception) {}
}
