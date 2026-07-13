package com.amni.drive.wire
import android.app.ActivityOptions
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import com.amni.drive.MainActivity
import com.amni.drive.link.H264Encoder
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
/**
 * Own-stack projection: Amni UI runs on a VirtualDisplay, H.264 encoded, emitted as frames.
 * Not Google templates. Feeders: Amni Link, AOA research pipe, any OutputStream.
 */
class AmniProjectionEngine(
private val context: Context,
private val width: Int = 1920,
private val height: Int = 720,
private val dpi: Int = 160,
private val fps: Int = 24
) {
private val encoder = H264Encoder(width, height, fps, 3_500_000)
private var virtualDisplay: VirtualDisplay? = null
private var worker: HandlerThread? = null
private var handler: Handler? = null
private val running = AtomicBoolean(false)
private val sink = AtomicReference<((ByteArray, Boolean) -> Unit)?>(null)
var onLog: (String) -> Unit = { Log.i(TAG, it) }
fun setFrameSink(cb: ((ByteArray, Boolean) -> Unit)?) { sink.set(cb) }
fun start(): Boolean {
if (!running.compareAndSet(false, true)) return true
return try {
encoder.onAccessUnit = { bytes, key -> sink.get()?.invoke(bytes, key) }
encoder.onError = { onLog("encoder: $it") }
if (!encoder.start()) {
running.set(false)
return false
}
val surface = encoder.surface() ?: run {
running.set(false)
return false
}
val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
virtualDisplay = dm.createVirtualDisplay(
"AmniDriveProjection", width, height, dpi, surface, flags
)
val displayId = virtualDisplay?.display?.displayId ?: -1
onLog("VirtualDisplay id=$displayId ${width}x$height@$fps")
val intent = MainActivity.openNavIntent(context)
var launched = false
if (displayId >= 0) {
try {
val opts = ActivityOptions.makeBasic()
opts.launchDisplayId = displayId
context.startActivity(intent, opts.toBundle())
launched = true
onLog("activity on virtual display $displayId")
} catch (e: Exception) {
onLog("launchDisplayId denied: ${e.message}")
}
}
if (!launched) {
try {
context.startActivity(intent)
onLog("activity on default display (encode may be black until VD allowed)")
launched = true
} catch (e: Exception) {
onLog("default launch failed: ${e.message}")
}
}
if (!launched) throw IllegalStateException("could not start MainActivity for projection")
val th = HandlerThread("amni-proj-drain").also { it.start() }
worker = th
handler = Handler(th.looper)
handler?.post(object : Runnable {
override fun run() {
if (!running.get()) return
encoder.drain(8_000L)
handler?.postDelayed(this, (1000L / fps).coerceAtLeast(16L))
}
})
encoder.requestKeyFrame()
onLog("projection started — full Amni UI on virtual display")
true
} catch (e: Exception) {
onLog("start failed: ${e.message}")
stop()
false
}
}
fun stop() {
running.set(false)
handler?.removeCallbacksAndMessages(null)
worker?.quitSafely()
worker = null
handler = null
try { virtualDisplay?.release() } catch (_: Exception) {}
virtualDisplay = null
encoder.stop()
sink.set(null)
onLog("projection stopped")
}
fun isRunning(): Boolean = running.get()
fun attachAoaWireSink(out: OutputStream) {
setFrameSink { nalu, key ->
try {
val frame = AaFrameCodec.video(nalu, key)
synchronized(out) { AaFrameCodec.write(out, frame) }
} catch (e: Exception) {
onLog("wire write fail: ${e.message}")
}
}
}
companion object {
private const val TAG = "Amni.Projection"
}
}
