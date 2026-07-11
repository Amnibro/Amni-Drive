package com.amni.drive.link
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
class LinkReceiver {
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val running = AtomicBoolean(false)
private val ctrl = AtomicReference<Socket?>(null)
private val video = AtomicReference<Socket?>(null)
private var job: Job? = null
private var videoJob: Job? = null
private var pingJob: Job? = null
var status: (String) -> Unit = {}
var onFrame: (Bitmap) -> Unit = {}
var onVideoSize: (Int, Int) -> Unit = { _, _ -> }
var connected: (Boolean) -> Unit = {}
fun connect(host: String, port: Int = LinkProto.PORT) {
if (!running.compareAndSet(false, true)) return
job = scope.launch {
try {
status("RECV · control $host:$port")
val s = Socket()
s.tcpNoDelay = true
s.connect(InetSocketAddress(host, port), 8000)
ctrl.set(s)
connected(true)
status("RECV · control linked")
LinkProto.write(s.getOutputStream(), LinkProto.HELLO, LinkProto.helloPayload("RECV", android.os.Build.MODEL))
launchVideo(host)
pingJob = launch {
while (isActive && running.get() && !s.isClosed) {
try { LinkProto.write(s.getOutputStream(), LinkProto.PING) } catch (_: Exception) { break }
delay(3000)
}
}
while (isActive && running.get() && !s.isClosed) {
val msg = LinkProto.read(s.getInputStream()) ?: break
when (msg.first) {
LinkProto.FRAME -> decodeFrame(msg.second)
LinkProto.VIDEO_CFG -> {
val cfg = LinkProto.parseVideoCfg(msg.second)
onVideoSize(cfg[0], cfg[1])
status("RECV · video ${cfg[0]}x${cfg[1]} @${cfg[2]}fps")
}
LinkProto.PONG, LinkProto.HELLO_ACK, LinkProto.HELLO -> {}
LinkProto.BYE -> break
else -> {}
}
}
} catch (e: Exception) {
status("RECV · ${e.message ?: "failed"}")
} finally {
ctrl.getAndSet(null)?.closeQuiet()
video.getAndSet(null)?.closeQuiet()
connected(false)
running.set(false)
status("RECV · disconnected")
}
}
}
private fun launchVideo(host: String) {
videoJob = scope.launch {
try {
val vs = Socket()
vs.tcpNoDelay = true
vs.connect(InetSocketAddress(host, LinkProto.VIDEO_PORT), 3000)
video.set(vs)
status("RECV · video channel linked :${LinkProto.VIDEO_PORT}")
while (isActive && running.get() && !vs.isClosed) {
val msg = LinkProto.read(vs.getInputStream()) ?: break
if (msg.first == LinkProto.FRAME) decodeFrame(msg.second)
}
} catch (_: Exception) {
status("RECV · video channel optional (using control for frames)")
}
}
}
private fun decodeFrame(bytes: ByteArray) {
val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
if (bmp != null) onFrame(bmp)
}
fun sendTouch(action: Int, xNorm: Float, yNorm: Float) {
val s = ctrl.get() ?: return
scope.launch {
try { LinkProto.write(s.getOutputStream(), LinkProto.TOUCH, LinkProto.touchPayload(action, xNorm, yNorm)) } catch (_: Exception) {}
}
}
fun stop() {
running.set(false)
pingJob?.cancel(); videoJob?.cancel(); job?.cancel()
ctrl.getAndSet(null)?.closeQuiet()
video.getAndSet(null)?.closeQuiet()
scope.cancel()
connected(false)
status("RECV · stopped")
}
private fun Socket.closeQuiet() = try { close() } catch (_: Exception) {}
}
