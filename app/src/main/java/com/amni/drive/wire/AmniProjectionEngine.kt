package com.amni.drive.wire

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
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
import com.amni.drive.link.H264Encoder
import com.amni.drive.ui.DrivePage
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Own-stack projection: mounts a RearPresentation on a VirtualDisplay backed by
 * H264Encoder's input surface (no privileged launchDisplayId needed) and emits
 * H.264 SPS/PPS + frames while accepting injected touch events from the car.
 */
class AmniProjectionEngine(
    private val context: Context,
    private val width: Int = 1920,
    private val height: Int = 720,
    private val dpi: Int = 160,
    private val fps: Int = 30,
    private val widthMargin: Int = 0,
    private val heightMargin: Int = 0
) {
    private val encoder = H264Encoder(width, height, fps, 5_000_000)
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: RearPresentation? = null
    private var worker: HandlerThread? = null
    private var handler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private val sink = AtomicReference<((ByteArray, Boolean) -> Unit)?>(null)
    private val typedSink = AtomicReference<((ByteArray, Boolean, Boolean, Long) -> Unit)?>(null)
    var onLog: (String) -> Unit = { Log.i(TAG, it) }

    fun setFrameSink(cb: ((ByteArray, Boolean) -> Unit)?) {
        sink.set(cb)
    }

    fun setFrameSinkTyped(cb: ((bytes: ByteArray, keyFrame: Boolean, codecConfig: Boolean, ptsUs: Long) -> Unit)?) {
        typedSink.set(cb)
    }

    fun requestKeyFrame() {
        encoder.requestKeyFrame()
        mainHandler.post { presentation?.window?.decorView?.invalidate() }
    }

    fun injectTouch(x: Float, y: Float, action: Int, downTime: Long, eventTime: Long) {
        mainHandler.post {
            val root = presentation?.window?.decorView ?: return@post
            val ev = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
            runCatching { root.dispatchTouchEvent(ev) }
            ev.recycle()
        }
    }

    fun start(): Boolean {
        if (!running.compareAndSet(false, true)) return true
        return try {
            encoder.onAccessUnit = { bytes, key -> sink.get()?.invoke(bytes, key) }
            encoder.onAccessUnitTyped = { bytes, key, cfg, pts -> typedSink.get()?.invoke(bytes, key, cfg, pts) }
            encoder.onError = { onLog("encoder: $it") }
            if (!encoder.start()) {
                running.set(false)
                return false
            }
            val surface = encoder.surface() ?: run {
                running.set(false)
                return false
            }

            val carDpi = (dpi * 1.55f).toInt().coerceIn(320, 420)
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
            val vd = dm.createVirtualDisplay(
                "AmniDriveProjection", width, height, carDpi, surface, flags
            )
            virtualDisplay = vd
            val display = vd.display ?: throw IllegalStateException("no virtual display")
            onLog("VirtualDisplay id=${display.displayId} ${width}x$height@$fps dpi=$carDpi (raw=$dpi) margins=${widthMargin}x${heightMargin}")

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
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                                .padding(
                                    horizontal = with(dens) { padX.toDp() },
                                    vertical = with(dens) { padY.toDp() }
                                )
                        ) {
                            RideDashboard(app, controller, DrivePage.HOME, surfaceDisplayId = display.displayId)
                        }
                    }
                    p.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
                    presentation = p
                    p.show()
                    onLog("Presentation shown displayId=${display.displayId}")
                } catch (t: Throwable) {
                    uiErr = t
                    onLog("Presentation fail: ${t.message}")
                } finally {
                    latch.countDown()
                }
            }
            latch.await(4, TimeUnit.SECONDS)
            uiErr?.let { throw it }

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
            onLog("projection started — full Amni UI on Presentation")
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
        mainHandler.post {
            runCatching { presentation?.dismiss() }
            presentation = null
        }
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        encoder.stop()
        sink.set(null)
        typedSink.set(null)
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
