package com.amni.drive.wire
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import com.amni.drive.AmniDriveApp
import com.amni.drive.DriveController
import com.amni.drive.aa.RideDashboard
import com.amni.drive.display.RearPresentation
import com.amni.drive.launcher.CarEmbed
import com.amni.drive.link.H264Encoder
import com.amni.drive.ui.DrivePage
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
class AmniProjectionEngine(private val context: Context, private val width: Int = 1920, private val height: Int = 720, private val dpi: Int = 160, private val fps: Int = 30, private val widthMargin: Int = 0, private val heightMargin: Int = 0, private val preview: Surface? = null) {
    private val encoder = H264Encoder(width, height, fps, 5_000_000)
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: RearPresentation? = null
    private var displayId = -1
    private var worker: HandlerThread? = null
    private var handler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private val sink = AtomicReference<((ByteArray, Boolean) -> Unit)?>(null)
    private val typedSink = AtomicReference<((ByteArray, Boolean, Boolean, Long) -> Unit)?>(null)
    var onLog: (String) -> Unit = { Log.i(TAG, it) }
    fun setFrameSink(cb: ((ByteArray, Boolean) -> Unit)?) = sink.set(cb)
    fun setFrameSinkTyped(cb: ((bytes: ByteArray, keyFrame: Boolean, codecConfig: Boolean, ptsUs: Long) -> Unit)?) = typedSink.set(cb)
    fun requestKeyFrame() { encoder.requestKeyFrame(); mainHandler.post { presentation?.window?.decorView?.invalidate() } }
    fun injectTouch(x: Float, y: Float, action: Int, downTime: Long, eventTime: Long) { mainHandler.post { val root = presentation?.window?.decorView ?: return@post; val ev = MotionEvent.obtain(downTime, eventTime, action, x, y, 0); runCatching { CarEmbed.route(root, ev, displayId) }; ev.recycle() } }
    fun dispatchKey(keyCode: Int, down: Boolean) { mainHandler.post { press(keyCode, if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP) } }
    fun moveFocus(keyCode: Int, steps: Int = 1) { mainHandler.post { repeat(steps.coerceIn(1, 10)) { press(keyCode, KeyEvent.ACTION_DOWN); press(keyCode, KeyEvent.ACTION_UP) } } }
    private fun press(keyCode: Int, action: Int) {
        val now = SystemClock.uptimeMillis()
        val rotary = keyCode == KeyEvent.KEYCODE_NAVIGATE_NEXT || keyCode == KeyEvent.KEYCODE_NAVIGATE_PREVIOUS
        val ev = KeyEvent(now, now, action, if (rotary) KeyEvent.KEYCODE_TAB else keyCode, 0, if (keyCode == KeyEvent.KEYCODE_NAVIGATE_PREVIOUS) KeyEvent.META_SHIFT_ON else 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM)
        when {
            keyCode == KeyEvent.KEYCODE_HOME -> if (action == KeyEvent.ACTION_UP) CarEmbed.home()
            keyCode in MEDIA_KEYS -> runCatching { (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).dispatchMediaKeyEvent(ev) }
            CarEmbed.key(ev, displayId) -> Unit
            else -> presentation?.window?.decorView?.let { root -> if (root.isInTouchMode && (rotary || keyCode in NAV_KEYS)) runCatching { root.requestFocusFromTouch() }; runCatching { root.dispatchKeyEvent(ev) } }
        }
    }
    fun start(): Boolean {
        if (!running.compareAndSet(false, true)) return true
        return try {
            encoder.onAccessUnit = { bytes, key -> sink.get()?.invoke(bytes, key) }
            encoder.onAccessUnitTyped = { bytes, key, cfg, pts -> typedSink.get()?.invoke(bytes, key, cfg, pts) }
            encoder.onError = { onLog("encoder: $it") }
            val surface = preview ?: (if (encoder.start()) encoder.surface() else null) ?: run { running.set(false); return false }
            val carDpi = carDensityDpi(width, height, widthMargin, heightMargin)
            val vd = (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).createVirtualDisplay("AmniDriveProjection", width, height, carDpi, surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION)
            virtualDisplay = vd
            val display = vd.display ?: throw IllegalStateException("no virtual display")
            displayId = display.displayId
            onLog("VirtualDisplay id=${display.displayId} ${width}x$height@$fps dpi=$carDpi (raw=$dpi) margins=${widthMargin}x$heightMargin preview=${preview != null}")
            val app = context.applicationContext as AmniDriveApp
            val latch = CountDownLatch(1)
            var uiErr: Throwable? = null
            mainHandler.post {
                try {
                    val controller = DriveController(context, app.configStore)
                    val padX = (widthMargin / 2f).coerceAtLeast(0f)
                    val padY = (heightMargin / 2f).coerceAtLeast(0f)
                    val p = RearPresentation(context, display) {
                        val dens = LocalDensity.current
                        Box(Modifier.fillMaxSize().background(Color.Black).padding(horizontal = with(dens) { padX.toDp() }, vertical = with(dens) { padY.toDp() })) { RideDashboard(app, controller, DrivePage.HOME, surfaceDisplayId = display.displayId, reserveAaChrome = false) }
                    }
                    p.window?.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
                    presentation = p
                    p.show()
                    onLog("Presentation shown displayId=${display.displayId}")
                } catch (t: Throwable) { uiErr = t; onLog("Presentation fail: ${t.message}") } finally { latch.countDown() }
            }
            latch.await(4, TimeUnit.SECONDS)
            uiErr?.let { throw it }
            if (preview == null) {
                val th = HandlerThread("amni-proj-drain").also { it.start() }
                worker = th
                handler = Handler(th.looper)
                handler?.post(object : Runnable { override fun run() { if (!running.get()) return; encoder.drain(8_000L); handler?.postDelayed(this, (1000L / fps).coerceAtLeast(16L)) } })
                encoder.requestKeyFrame()
            }
            onLog("projection started, full Amni UI on Presentation")
            true
        } catch (e: Exception) { onLog("start failed: ${e.message}"); stop(); false }
    }
    fun stop() {
        running.set(false)
        handler?.removeCallbacksAndMessages(null)
        worker?.quitSafely()
        worker = null
        handler = null
        val id = displayId
        mainHandler.post { runCatching { presentation?.dismiss() }; presentation = null; CarEmbed.drop(id) }
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        if (preview == null) encoder.stop()
        sink.set(null)
        typedSink.set(null)
        onLog("projection stopped")
    }
    fun isRunning(): Boolean = running.get()
    fun attachAoaWireSink(out: OutputStream) = setFrameSink { nalu, key -> runCatching { val frame = AaFrameCodec.video(nalu, key); synchronized(out) { AaFrameCodec.write(out, frame) } }.onFailure { onLog("wire write fail: ${it.message}") } }
    companion object {
        private const val TAG = "Amni.Projection"
        private val MEDIA_KEYS = setOf(KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE)
        private val NAV_KEYS = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_TAB)
        const val TARGET_CAR_HEIGHT_DP = 340f
        const val MIN_CAR_WIDTH_DP = 720f
        fun carDensityDpi(width: Int, height: Int, widthMargin: Int, heightMargin: Int): Int { val visW = (width - widthMargin).coerceAtLeast(1); val visH = (height - heightMargin).coerceAtLeast(1); return (minOf(visH / TARGET_CAR_HEIGHT_DP, visW / MIN_CAR_WIDTH_DP).coerceIn(1f, 3f) * 160f).toInt() }
    }
}
