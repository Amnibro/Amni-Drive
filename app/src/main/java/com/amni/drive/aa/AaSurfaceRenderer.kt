package com.amni.drive.aa
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.amni.drive.AmniDriveApp
import com.amni.drive.DriveController
import com.amni.drive.display.RearPresentation
import com.amni.drive.map.GeoMath
import com.amni.drive.map.TileStore
import com.amni.drive.ui.DrivePage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.floor
import kotlin.math.pow
class AaSurfaceRenderer(private val carContext: CarContext, lifecycle: Lifecycle) : SurfaceCallback {
private val app get() = carContext.applicationContext as AmniDriveApp
private val tiles: TileStore get() = app.tileStore
private val scope = CoroutineScope(Dispatchers.Default + Job())
private val main = Handler(Looper.getMainLooper())
private val presentation = AtomicReference<RearPresentation?>(null)
@Volatile private var container: SurfaceContainer? = null
@Volatile private var virtualDisplay: VirtualDisplay? = null
@Volatile private var fullUi = false
@Volatile private var zoom = 15.5
@Volatile private var panX = 0f
@Volatile private var panY = 0f
@Volatile private var lastTouchX = 0f
@Volatile private var lastTouchY = 0f
@Volatile private var pointerDown = false
@Volatile private var gestureDownTime = 0L
private var loop: Job? = null
private val bg = Paint().apply { color = Color.rgb(8, 10, 14) }
private val acc = Paint().apply { color = Color.rgb(0, 255, 157); isAntiAlias = true }
private val accStroke = Paint().apply { color = Color.rgb(0, 255, 157); style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
private val route = Paint().apply { color = Color.rgb(0, 255, 157); strokeWidth = 10f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; isAntiAlias = true }
private val routeGlow = Paint().apply { color = Color.argb(70, 0, 255, 157); strokeWidth = 22f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
private val hud = Paint().apply { color = Color.rgb(0, 255, 157); textSize = 36f; isAntiAlias = true; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
private val hudDim = Paint().apply { color = Color.argb(200, 200, 214, 220); textSize = 24f; isAntiAlias = true; typeface = Typeface.MONOSPACE }
private val cardBg = Paint().apply { color = Color.argb(190, 12, 16, 22); isAntiAlias = true }
private val cardLine = Paint().apply { color = Color.argb(80, 0, 255, 157); style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true }
private val white = Paint().apply { color = Color.WHITE; isAntiAlias = true }
private val puckRing = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true }
private val dock = Paint().apply { color = Color.argb(210, 6, 8, 12) }
init {
lifecycle.addObserver(object : DefaultLifecycleObserver {
override fun onDestroy(owner: LifecycleOwner) { releaseFullUi(); scope.coroutineContext[Job]?.cancel() }
})
}
override fun onSurfaceAvailable(c: SurfaceContainer) {
container = c
fullUi = false
Log.i(TAG, "surface available ${c.width}x${c.height} dpi=${c.dpi}")
main.post {
if (container !== c) return@post
val ok = runCatching { startFullUi(c) }.getOrDefault(false)
fullUi = ok
if (ok) Log.i(TAG, "surface fullUi=true Presentation") else {
Log.i(TAG, "surface fullUi=false canvas fallback")
startLoop()
}
}
}
override fun onSurfaceDestroyed(c: SurfaceContainer) {
loop?.cancel()
releaseFullUi()
container = null
Log.i(TAG, "surface destroyed")
}
override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
if (fullUi) return
zoom = (zoom + (scaleFactor - 1f)).coerceIn(11.0, 18.0)
}
override fun onScroll(distanceX: Float, distanceY: Float) {
if (fullUi) {
val c = container
if (!pointerDown) {
pointerDown = true
gestureDownTime = SystemClock.uptimeMillis()
if (lastTouchX == 0f && lastTouchY == 0f && c != null) {
lastTouchX = c.width / 2f
lastTouchY = c.height / 2f
}
injectTouch(lastTouchX, lastTouchY, MotionEvent.ACTION_DOWN, gestureDownTime, gestureDownTime)
}
lastTouchX = (lastTouchX - distanceX).coerceIn(0f, (c?.width ?: 1).toFloat())
lastTouchY = (lastTouchY - distanceY).coerceIn(0f, (c?.height ?: 1).toFloat())
injectTouch(lastTouchX, lastTouchY, MotionEvent.ACTION_MOVE, gestureDownTime, SystemClock.uptimeMillis())
} else {
panX += distanceX
panY += distanceY
}
}
override fun onClick(x: Float, y: Float) {
if (fullUi) {
if (pointerDown) {
injectTouch(lastTouchX, lastTouchY, MotionEvent.ACTION_UP, gestureDownTime, SystemClock.uptimeMillis())
pointerDown = false
}
lastTouchX = x
lastTouchY = y
val t0 = SystemClock.uptimeMillis()
injectTouch(x, y, MotionEvent.ACTION_DOWN, t0, t0)
main.postDelayed({
injectTouch(x, y, MotionEvent.ACTION_UP, t0, SystemClock.uptimeMillis())
}, 24)
} else {
panX = 0f
panY = 0f
}
}
override fun onFling(velocityX: Float, velocityY: Float) {
if (fullUi && pointerDown) {
injectTouch(lastTouchX, lastTouchY, MotionEvent.ACTION_UP, gestureDownTime, SystemClock.uptimeMillis())
pointerDown = false
}
}
private fun startFullUi(c: SurfaceContainer): Boolean {
val surface = c.surface ?: return false
val w = c.width
val h = c.height
val dpi = if (c.dpi > 0) c.dpi else 160
if (w <= 0 || h <= 0) return false
return runCatching {
val dm = carContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
val vd = dm.createVirtualDisplay("AmniAaRide", w, h, dpi, surface, flags)
virtualDisplay = vd
val display = vd.display ?: throw IllegalStateException("no virtual display")
val dCtx = carContext.createDisplayContext(display)
val controller = DriveController(dCtx, app.configStore)
val p = RearPresentation(dCtx, display) {
RideDashboard(app, controller, DrivePage.HOME, surfaceDisplayId = display.displayId)
}
presentation.set(p)
p.show()
Log.i(TAG, "RIDE Presentation shown displayId=${display.displayId} ${w}x${h}")
true
}.getOrElse {
Log.e(TAG, "RIDE VD fail ${it.message}", it)
releaseFullUi()
false
}
}
private fun injectTouch(x: Float, y: Float, action: Int, downTime: Long = SystemClock.uptimeMillis(), eventTime: Long = downTime) {
main.post {
val p = presentation.get() ?: return@post
val root = p.window?.decorView ?: return@post
val c = container
val rw = root.width.coerceAtLeast(1)
val rh = root.height.coerceAtLeast(1)
val sx = if (c != null && c.width > 0) x * rw / c.width else x
val sy = if (c != null && c.height > 0) y * rh / c.height else y
val ev = MotionEvent.obtain(downTime, eventTime, action, sx, sy, 0)
runCatching { root.dispatchTouchEvent(ev) }.onFailure { Log.w(TAG, "touch $action fail ${it.message}") }
ev.recycle()
}
}
private fun releaseFullUi() {
main.post {
runCatching { presentation.getAndSet(null)?.dismiss() }
}
runCatching { virtualDisplay?.release() }
virtualDisplay = null
fullUi = false
}
private fun startLoop() {
loop?.cancel()
loop = scope.launch { while (isActive) { runCatching { drawFrame() }; delay(90) } }
}
private suspend fun drawFrame() {
val c = container ?: return
val surface = c.surface ?: return
val w = c.width
val h = c.height
if (w <= 0 || h <= 0) return
val loc = app.tracker.location.value
val r = AaNavState.route
val dest = AaNavState.destination
val fallback = r?.points?.firstOrNull()
val lat = loc?.latitude ?: fallback?.lat ?: 39.5
val lon = loc?.longitude ?: fallback?.lon ?: -98.35
val z = floor(zoom).toInt()
val tilePx = (512.0 * 2.0.pow(zoom - z)).toFloat()
val cx = GeoMath.lonToTileX(lon, z) - panX / tilePx
val cy = GeoMath.latToTileY(lat, z) - panY / tilePx
val cols = (w / tilePx).toInt() + 3
val rows = (h / tilePx).toInt() + 3
val x0 = floor(cx - cols / 2.0).toInt()
val y0 = floor(cy - rows / 2.0).toInt()
val placed = ArrayList<Triple<android.graphics.Bitmap, Float, Float>>()
for (tx in x0..x0 + cols) for (ty in y0..y0 + rows) {
if (ty < 0 || ty >= 1 shl z) continue
val bmp = tiles.tile(0, z, ((tx % (1 shl z)) + (1 shl z)) % (1 shl z), ty) ?: continue
val px = (w / 2f) + (tx - cx).toFloat() * tilePx
val py = (h / 2f) + (ty - cy).toFloat() * tilePx
placed.add(Triple(bmp, px, py))
}
val canvas = runCatching { surface.lockCanvas(null) }.getOrNull() ?: return
runCatching {
canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
placed.forEach { (bmp, px, py) -> canvas.drawBitmap(bmp, null, RectF(px, py, px + tilePx, py + tilePx), null) }
r?.let { rr ->
if (rr.points.size > 1) {
val path = Path()
rr.points.forEachIndexed { i, p ->
val tX = GeoMath.lonToTileX(p.lon, z)
val tY = GeoMath.latToTileY(p.lat, z)
val px = (w / 2f) + (tX - cx).toFloat() * tilePx
val py = (h / 2f) + (tY - cy).toFloat() * tilePx
if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
}
canvas.drawPath(path, routeGlow)
canvas.drawPath(path, route)
}
}
canvas.drawCircle(w / 2f, h / 2f, 16f, acc)
canvas.drawCircle(w / 2f, h / 2f, 22f, puckRing)
val topH = 100f
canvas.drawRect(0f, 0f, w.toFloat(), topH, dock)
canvas.drawLine(0f, topH, w.toFloat(), topH, accStroke)
canvas.drawText("AMNI//DRV", 28f, 44f, hud)
canvas.drawText("RIDE FALLBACK · canvas (Presentation failed)", 28f, 78f, hudDim)
val spd = loc?.let { if (it.hasSpeed()) (it.speed * 2.23694f).toInt() else null }
val spdTxt = if (spd != null) "$spd mph" else "-- mph"
val spdPaint = Paint(hud).apply { textSize = 48f; textAlign = Paint.Align.RIGHT }
canvas.drawText(spdTxt, w - 28f, 62f, spdPaint)
val cardW = (w * 0.28f).coerceIn(220f, 360f)
val cardH = 150f
val left = 20f
val top = topH + 20f
drawCard(canvas, left, top, cardW, cardH, "NAV", dest?.name ?: "No destination", if (r != null) AaHomeScreen.formatRoute(r.distanceM, r.durationS) else "Search on phone or car list")
drawCard(canvas, left, top + cardH + 14f, cardW, 120f, "TRIP", if (loc != null) String.format("%.4f, %.4f", lat, lon) else "GPS acquiring", "zoom ${"%.1f".format(zoom)}")
val right = w - cardW - 20f
drawCard(canvas, right, top, cardW, 120f, "GATE", "driver policy active", "rear ungated · override on phone")
drawCard(canvas, right, top + 134f, cardW, 120f, "SESSION", "Gearhead holds USB/TLS", "Canvas fallback UI")
val dockH = 88f
val dy = h - dockH
canvas.drawRect(0f, dy, w.toFloat(), h.toFloat(), dock)
canvas.drawLine(0f, dy, w.toFloat(), dy, accStroke)
val tabs = listOf("HOME", "NAV", "APPS", "MEDIA", "SETTINGS")
val slot = w / tabs.size.toFloat()
tabs.forEachIndexed { i, t ->
val cxTab = slot * i + slot / 2f
val active = t == "NAV"
val tp = Paint(if (active) hud else hudDim).apply { textAlign = Paint.Align.CENTER; textSize = if (active) 28f else 24f }
canvas.drawText(t, cxTab, dy + 54f, tp)
if (active) canvas.drawCircle(cxTab, dy + 72f, 5f, acc)
}
canvas.drawText("FULL AMNI UI · canvas fallback", w / 2f, h - 12f, Paint(hudDim).apply { textAlign = Paint.Align.CENTER; textSize = 16f })
}
runCatching { surface.unlockCanvasAndPost(canvas) }
}
private fun drawCard(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, title: String, line1: String, line2: String) {
val r = RectF(x, y, x + w, y + h)
canvas.drawRoundRect(r, 18f, 18f, cardBg)
canvas.drawRoundRect(r, 18f, 18f, cardLine)
canvas.drawText(title, x + 18f, y + 36f, Paint(hud).apply { textSize = 22f })
canvas.drawText(line1.take(28), x + 18f, y + 72f, Paint(white).apply { textSize = 22f; isAntiAlias = true; typeface = Typeface.MONOSPACE })
canvas.drawText(line2.take(32), x + 18f, y + 104f, Paint(hudDim).apply { textSize = 18f })
}
companion object {
const val TAG = "Amni.AaSurface"
fun attach(carContext: CarContext, lifecycle: Lifecycle) {
runCatching {
carContext.getCarService(AppManager::class.java).setSurfaceCallback(AaSurfaceRenderer(carContext, lifecycle))
Log.i(TAG, "surface callback attached")
}.onFailure { Log.e(TAG, "attach fail ${it.message}", it) }
}
}
}
