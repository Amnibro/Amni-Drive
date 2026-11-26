package com.amni.drive.launcher
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Activity
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Path
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.MotionEvent
import android.view.Surface
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
object CarMirror {
    private const val TAG = "Amni.Mirror"
    val aspect = MutableStateFlow(0.5f)
    val live = MutableStateFlow(false)
    val pkg = MutableStateFlow<String?>(null)
    @Volatile var area: IntArray? = null
    private var projection: MediaProjection? = null
    private var vd: VirtualDisplay? = null
    private var surface: Surface? = null
    private var sw = 0
    private var sh = 0
    private var pending: String? = null
    private val main = Handler(Looper.getMainLooper())
    private val pts = ArrayList<FloatArray>()
    private var downAt = 0L
    private fun phone(ctx: Context): DisplayMetrics = DisplayMetrics().also { m -> @Suppress("DEPRECATION") (ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(m) }
    private val rotation = object : DisplayManager.DisplayListener { override fun onDisplayAdded(id: Int) = Unit; override fun onDisplayRemoved(id: Int) = Unit; override fun onDisplayChanged(id: Int) { if (id == Display.DEFAULT_DISPLAY) app?.let { measure(it) } } }
    private var app: Context? = null
    private fun measure(ctx: Context) { phone(ctx).let { m -> aspect.value = m.widthPixels.toFloat() / m.heightPixels.coerceAtLeast(1) } }
    fun locked(ctx: Context): Boolean = (ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
    fun touchReady(): Boolean = MirrorTouch.me != null
    fun open(ctx: Context, pkg: String) {
        val c = ctx.applicationContext
        app = c
        measure(c)
        this.pkg.value = pkg
        if (projection != null) launch(c, pkg) else { pending = pkg; runCatching { c.startActivity(Intent(c, MirrorGrant::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { Log.w(TAG, "grant start failed: ${it.message}") } }
    }
    private fun launch(ctx: Context, pkg: String) { runCatching { ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)) } }.onFailure { Log.w(TAG, "launch $pkg failed: ${it.message}") } }
    fun granted(svc: Context, code: Int, data: Intent) {
        val c = svc.applicationContext
        val mp = runCatching { (c.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(code, data) }.getOrNull() ?: run { Log.w(TAG, "no projection"); return }
        mp.registerCallback(object : MediaProjection.Callback() { override fun onStop() { Log.i(TAG, "projection stopped"); drop(); c.stopService(Intent(c, MirrorService::class.java)) } }, main)
        projection = mp
        (c.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).registerDisplayListener(rotation, main)
        live.value = true
        Log.i(TAG, "projection granted")
        frame()
        pending?.let { launch(c, it) }
        pending = null
    }
    fun surface(s: Surface?, w: Int, h: Int) { surface = s?.takeIf { it.isValid }; sw = w; sh = h; frame() }
    private fun frame() {
        val mp = projection ?: return
        val s = surface
        val c = app ?: return
        if (s == null) { runCatching { vd?.surface = null }; return }
        runCatching { vd?.let { it.resize(sw, sh, phone(c).densityDpi * sh / phone(c).heightPixels.coerceAtLeast(1)); it.surface = s } ?: run { vd = mp.createVirtualDisplay("AmniDriveMirror", sw, sh, (phone(c).densityDpi * sh / phone(c).heightPixels.coerceAtLeast(1)).coerceAtLeast(120), DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, s, null, main) } }.onFailure { Log.w(TAG, "frame failed: ${it.message}") }
        Log.i(TAG, "mirror ${sw}x$sh")
    }
    fun touch(ev: MotionEvent, left: Int, top: Int) {
        val c = app ?: return
        if (sw <= 0 || sh <= 0) return
        val m = phone(c)
        val p = floatArrayOf(((ev.x - left) / sw * m.widthPixels).coerceIn(0f, m.widthPixels - 1f), ((ev.y - top) / sh * m.heightPixels).coerceIn(0f, m.heightPixels - 1f))
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pts.clear(); pts.add(p); downAt = ev.eventTime }
            MotionEvent.ACTION_MOVE -> if (pts.isNotEmpty()) pts.add(p)
            MotionEvent.ACTION_UP -> if (pts.isNotEmpty()) { pts.add(p); MirrorTouch.me?.stroke(pts, (ev.eventTime - downAt).coerceIn(40L, 7000L)) ?: Log.w(TAG, "touch service is off"); pts.clear() }
            MotionEvent.ACTION_CANCEL -> pts.clear()
        }
    }
    fun back(): Boolean = MirrorTouch.me?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) ?: false
    fun askTouch(ctx: Context) { runCatching { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    private fun drop() { runCatching { vd?.release() }; vd = null; projection = null; live.value = false; app?.let { runCatching { (it.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).unregisterDisplayListener(rotation) } } }
    fun stop(ctx: Context) { runCatching { projection?.stop() }; drop(); ctx.applicationContext.stopService(Intent(ctx.applicationContext, MirrorService::class.java)) }
}
class MirrorGrant : Activity() {
    override fun onCreate(b: Bundle?) { super.onCreate(b); @Suppress("DEPRECATION") startActivityForResult((getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent(), 7) }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        if (res == RESULT_OK && data != null) Intent(this, MirrorService::class.java).putExtra("code", res).putExtra("data", data).let { if (Build.VERSION.SDK_INT >= 26) startForegroundService(it) else startService(it) }
        finish()
    }
}
class MirrorService : Service() {
    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, flags: Int, id: Int): Int {
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("amni_mirror", "Apps on the car screen", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "amni_mirror").setContentTitle("Amni-Drive").setContentText("Showing your phone's app on the car screen").setSmallIcon(android.R.drawable.ic_menu_slideshow).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(77, n)
        @Suppress("DEPRECATION") val data = i?.getParcelableExtra<Intent>("data")
        if (i != null && data != null) CarMirror.granted(this, i.getIntExtra("code", 0), data) else stopSelf()
        return START_NOT_STICKY
    }
}
class MirrorTouch : AccessibilityService() {
    override fun onServiceConnected() { me = this }
    override fun onUnbind(i: Intent?): Boolean { me = null; return super.onUnbind(i) }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    fun stroke(pts: List<FloatArray>, ms: Long) { val path = Path().apply { moveTo(pts[0][0], pts[0][1]); pts.drop(1).forEach { lineTo(it[0], it[1]) } }; dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, ms)).build(), null, null) }
    companion object { @Volatile var me: MirrorTouch? = null }
}
