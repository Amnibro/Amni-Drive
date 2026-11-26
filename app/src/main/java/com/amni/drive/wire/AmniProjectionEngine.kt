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
    private val heightMargin: Int = 0,
    private val directSurface: android.view.Surface? = null
) {
    private val encoder = H264Encoder(width, height, fps, 2_400_000)
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: RearPresentation? = null
    private var drainThread: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private val sink = AtomicReference<((ByteArray, Boolean) -> Unit)?>(null)
    private val typedSink = AtomicReference<((ByteArray, Boolean, Boolean, Long) -> Unit)?>(null)
    private data class QueuedMove(val x: Float, val y: Float, val downTime: Long, val eventTime: Long)
    private val pendingMove = AtomicReference<QueuedMove?>(null)
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

    private fun dispatchTouchNow(x: Float, y: Float, action: Int, downTime: Long, eventTime: Long) {
        val root = presentation?.window?.decorView ?: return
        val displayId = virtualDisplay?.display?.displayId ?: -1
        val ev = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        runCatching {
            if (displayId >= 0) com.amni.drive.launcher.CarEmbed.route(root, ev, displayId)
            else root.dispatchTouchEvent(ev)
        }.onFailure { onLog("touch err: ${it.message}") }
        ev.recycle()
    }

    fun injectTouch(x: Float, y: Float, action: Int, downTime: Long, eventTime: Long) {
        if (action == MotionEvent.ACTION_MOVE) {
            val prev = pendingMove.getAndSet(QueuedMove(x, y, downTime, eventTime))
            if (prev == null) {
                mainHandler.post {
                    val m = pendingMove.getAndSet(null) ?: return@post
                    dispatchTouchNow(m.x, m.y, MotionEvent.ACTION_MOVE, m.downTime, m.eventTime)
                }
            }
            return
        }
        val flushed = pendingMove.getAndSet(null)
        mainHandler.post {
            if (flushed != null && action == MotionEvent.ACTION_UP) {
                dispatchTouchNow(flushed.x, flushed.y, MotionEvent.ACTION_MOVE, flushed.downTime, flushed.eventTime)
            }
            dispatchTouchNow(x, y, action, downTime, eventTime)
        }
    }

    fun dispatchKey(keyCode: Int, down: Boolean) {
        mainHandler.post {
            val root = presentation?.window?.decorView ?: return@post
            val displayId = virtualDisplay?.display?.displayId ?: -1
            if (isNavigationKey(keyCode) && root.isInTouchMode) {
                runCatching { root.requestFocusFromTouch() }
            }
            val now = android.os.SystemClock.uptimeMillis()
            val action = if (down) android.view.KeyEvent.ACTION_DOWN else android.view.KeyEvent.ACTION_UP
            val mapped = mapRotaryKeyCode(keyCode)
            val meta = if (keyCode == android.view.KeyEvent.KEYCODE_NAVIGATE_PREVIOUS) {
                android.view.KeyEvent.META_SHIFT_ON
            } else {
                0
            }
            val ev = android.view.KeyEvent(
                now,
                now,
                action,
                mapped,
                0,
                meta,
                android.view.KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                android.view.KeyEvent.FLAG_FROM_SYSTEM,
            )
            runCatching {
                if (displayId >= 0 && com.amni.drive.launcher.CarEmbed.key(ev, displayId)) return@runCatching
                root.dispatchKeyEvent(ev)
            }.onFailure { onLog("key err: ${it.message}") }
        }
    }

    fun moveFocus(keyCode: Int, steps: Int = 1) {
        val count = steps.coerceIn(1, 10)
        mainHandler.post {
            val root = presentation?.window?.decorView ?: return@post
            if (root.isInTouchMode) {
                runCatching { root.requestFocusFromTouch() }
            }
            val mapped = mapRotaryKeyCode(keyCode)
            val meta = if (keyCode == android.view.KeyEvent.KEYCODE_NAVIGATE_PREVIOUS) {
                android.view.KeyEvent.META_SHIFT_ON
            } else {
                0
            }
            repeat(count) {
                val now = android.os.SystemClock.uptimeMillis()
                runCatching {
                    root.dispatchKeyEvent(
                        android.view.KeyEvent(
                            now,
                            now,
                            android.view.KeyEvent.ACTION_DOWN,
                            mapped,
                            0,
                            meta,
                            android.view.KeyCharacterMap.VIRTUAL_KEYBOARD,
                            0,
                            android.view.KeyEvent.FLAG_FROM_SYSTEM,
                        )
                    )
                    root.dispatchKeyEvent(
                        android.view.KeyEvent(
                            now,
                            now,
                            android.view.KeyEvent.ACTION_UP,
                            mapped,
                            0,
                            meta,
                            android.view.KeyCharacterMap.VIRTUAL_KEYBOARD,
                            0,
                            android.view.KeyEvent.FLAG_FROM_SYSTEM,
                        )
                    )
                }
            }
        }
    }

    private fun isNavigationKey(keyCode: Int): Boolean = when (keyCode) {
        android.view.KeyEvent.KEYCODE_DPAD_UP,
        android.view.KeyEvent.KEYCODE_DPAD_DOWN,
        android.view.KeyEvent.KEYCODE_DPAD_LEFT,
        android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
        android.view.KeyEvent.KEYCODE_ENTER,
        android.view.KeyEvent.KEYCODE_TAB,
        android.view.KeyEvent.KEYCODE_NAVIGATE_NEXT,
        android.view.KeyEvent.KEYCODE_NAVIGATE_PREVIOUS -> true
        else -> false
    }

    private fun mapRotaryKeyCode(keyCode: Int): Int = when (keyCode) {
        android.view.KeyEvent.KEYCODE_NAVIGATE_NEXT,
        android.view.KeyEvent.KEYCODE_NAVIGATE_PREVIOUS -> android.view.KeyEvent.KEYCODE_TAB
        else -> keyCode
    }

    fun start(): Boolean {
        if (!running.compareAndSet(false, true)) return true
        return try {
            encoder.onAccessUnit = { bytes, key -> sink.get()?.invoke(bytes, key) }
            encoder.onAccessUnitTyped = { bytes, key, cfg, pts -> typedSink.get()?.invoke(bytes, key, cfg, pts) }
            encoder.onError = { onLog("encoder: $it") }
            val surface = directSurface ?: run {
                if (!encoder.start()) {
                    running.set(false)
                    return false
                }
                encoder.surface() ?: run {
                    running.set(false)
                    return false
                }
            }

            val effH = (height - heightMargin).coerceAtLeast(360)
            val carDpi = if (effH < 600) {
                (dpi * 1.35f).toInt().coerceIn(200, 280)
            } else {
                (dpi * 1.62f).toInt().coerceIn(320, 430)
            }
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
                        val baseDens = LocalDensity.current
                        val carFontScale = (baseDens.fontScale * 1.24f).coerceIn(1.20f, 1.40f)
                        androidx.compose.runtime.CompositionLocalProvider(
                            LocalDensity provides androidx.compose.ui.unit.Density(baseDens.density, fontScale = carFontScale)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color.Black)
                                    .padding(
                                        horizontal = with(baseDens) { padX.toDp() },
                                        vertical = with(baseDens) { padY.toDp() }
                                    )
                            ) {
                                RideDashboard(app, controller, DrivePage.HOME, surfaceDisplayId = display.displayId)
                            }
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

            if (directSurface == null) {
                drainThread = Thread({
                    runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DISPLAY) }
                    while (running.get()) {
                        encoder.drain(15_000L)
                    }
                }, "amni-proj-drain").also {
                    it.isDaemon = true
                    it.start()
                }
                encoder.requestKeyFrame()
            }
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
        drainThread?.interrupt()
        drainThread = null
        val droppedId = virtualDisplay?.display?.displayId ?: -1
        mainHandler.post {
            if (droppedId >= 0) runCatching { com.amni.drive.launcher.CarEmbed.drop(droppedId) }
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
