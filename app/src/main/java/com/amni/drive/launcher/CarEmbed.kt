package com.amni.drive.launcher
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import kotlinx.coroutines.flow.MutableStateFlow
object CarEmbed {
    const val ACTION = "com.amniscient.DASH_INPUT"
    const val MAP_PKG = "com.amniscient.map"
    private const val HELLO = 1
    private const val TOUCH = 2
    private const val KEY = 3
    private const val SURFACE = 4
    private const val GONE = 5
    private const val LOCATION = 6
    private const val TEXT = 7
    private const val VOICE_REQ = 8
    private const val KB_REQ = 9
    private const val CONFIG = 10
    private const val TAG = "Amni.Embed"
    data class Shown(val pkg: String, val label: String, val displayId: Int, val at: Long, val front: Boolean = true, val mirror: Boolean = false, val slot: String? = null)
    val shown = MutableStateFlow<Shown?>(null)
    val voicePulse = MutableStateFlow(0L)
    val kbPulse = MutableStateFlow(0L)
    private var remote: Messenger? = null
    private var conn: ServiceConnection? = null
    private var bound: Context? = null
    private var target = 0
    private var frame: Bundle? = null
    private var pendingQuery: Bundle? = null
    private var latestConfig: Bundle? = null
    @Volatile var overlayBlocking: Boolean = false
    @Volatile var tileKeyboardBlocking: Boolean = false
    private var area: IntArray? = null
    private val main = Handler(Looper.getMainLooper())
    private val ipcThread = android.os.HandlerThread("amni-embed-ipc").apply { isDaemon = true; start() }
    private val ipc = Handler(ipcThread.looper)
    private val inbox = Messenger(Handler(Looper.getMainLooper()) { m ->
        when (m.what) {
            GONE -> if (shown.value?.let { SystemClock.uptimeMillis() - it.at > 777 } == true) { Log.i(TAG, "app asked to go back home"); home() }
            VOICE_REQ -> voicePulse.value = SystemClock.uptimeMillis()
            KB_REQ -> kbPulse.value = SystemClock.uptimeMillis()
        }
        true
    })
    fun supports(ctx: Context, pkg: String): Boolean = runCatching { ctx.packageManager.resolveService(Intent(ACTION).setPackage(pkg), 0) != null }.getOrDefault(false)
    fun up(s: Shown?, displayId: Int?): Boolean = s != null && s.front && displayId != null && s.displayId == displayId
    fun on(displayId: Int?): Shown? = shown.value?.takeIf { up(it, displayId) }
    fun open(ctx: Context, entry: AppEntry, displayId: Int): Boolean {
        val cur = shown.value
        if (cur?.pkg != entry.packageName || cur.displayId != displayId || conn == null) { if (!bind(ctx.applicationContext, entry.packageName)) return false }
        shown.value = Shown(entry.packageName, entry.label, displayId, SystemClock.uptimeMillis())
        target = 0
        Log.i(TAG, "show ${entry.packageName} on display $displayId")
        return true
    }
    fun mirror(ctx: Context, entry: AppEntry, displayId: Int): Boolean {
        if (CarMirror.locked(ctx)) return false
        shown.value = Shown(entry.packageName, entry.label, displayId, SystemClock.uptimeMillis(), mirror = true)
        target = 0
        CarMirror.open(ctx, entry.packageName)
        Log.i(TAG, "mirror ${entry.packageName} on display $displayId")
        return true
    }
    fun slot(pkg: String, label: String, displayId: Int, slot: String) { shown.value = Shown(pkg, label, displayId, SystemClock.uptimeMillis(), slot = slot); target = 0 }
    fun attach(ctx: Context, pkg: String) { if (conn == null) bind(ctx.applicationContext, pkg) }
    fun full(pkg: String, displayId: Int, mirror: Boolean) { shown.value = Shown(pkg, pkg, displayId, SystemClock.uptimeMillis(), mirror = mirror) }
    fun home() { shown.value = shown.value?.copy(front = false); target = 0 }
    fun drop(displayId: Int) { shown.value = null; target = 0; area = null; overlayBlocking = false; tileKeyboardBlocking = false; CarMirror.area = null; CarApps.drop(); unbind() }
    fun surface(s: Surface?, w: Int, h: Int, dpi: Int) { frame = Bundle().apply { putParcelable("s", s); putInt("w", w); putInt("h", h); putInt("dpi", dpi) }; sendFrame() }
    fun area(a: IntArray?) { area = a }
    private fun hit(a: IntArray?, ev: MotionEvent): Boolean = a != null && ev.x >= a[0] && ev.x < a[2] && ev.y >= a[1] && ev.y < a[3]
    fun route(root: View, ev: MotionEvent, displayId: Int): Boolean {
        if (overlayBlocking || tileKeyboardBlocking) {
            target = 0
            return root.dispatchTouchEvent(ev)
        }
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) target = if (CarApps.pick(ev)) 3 else if (hit(CarMirror.area, ev)) 2 else if (hit(area, ev) && remote != null) 1 else 0
        if (target == 3) { CarApps.touch(ev); return true }
        val a = (if (target == 2) CarMirror.area else if (target == 1) area else null) ?: return root.dispatchTouchEvent(ev)
        if (target == 2) {
            CarMirror.touch(ev, a[0], a[1])
        } else {
            val copy = MotionEvent.obtain(ev).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
                offsetLocation(-a[0].toFloat(), -a[1].toFloat())
            }
            ipc.post {
                try {
                    remote?.send(Message.obtain(null, TOUCH).apply { obj = copy })
                } catch (e: Exception) {
                    Log.w(TAG, "send TOUCH failed: ${e.message}")
                } finally {
                    copy.recycle()
                }
            }
        }
        return true
    }
    fun key(ev: KeyEvent, displayId: Int): Boolean = on(displayId)?.let { if (it.slot != null) return CarApps.key(it.slot, ev); if (it.mirror) return (ev.keyCode == KeyEvent.KEYCODE_BACK || ev.keyCode == KeyEvent.KEYCODE_ESCAPE) && (ev.action != KeyEvent.ACTION_UP || CarMirror.back() || true); send(Message.obtain(null, KEY).apply { obj = KeyEvent(ev) }); true } ?: false
    fun sendKeyCode(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0, android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM)
        val up = KeyEvent(now, now + 12L, KeyEvent.ACTION_UP, keyCode, 0, 0, android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM)
        send(Message.obtain(null, KEY).apply { obj = down })
        send(Message.obtain(null, KEY).apply { obj = up })
    }
    fun sendQuery(text: String, submit: Boolean = false, autoGo: Boolean = false) {
        val b = Bundle().apply { putString("q", text); putBoolean("go", submit); putBoolean("autoGo", autoGo) }
        pendingQuery = b
        if (remote != null) send(Message.obtain(null, TEXT).apply { data = Bundle(b) })
    }
    fun sendConfig(b: Bundle) {
        val copy = Bundle(b)
        latestConfig = copy
        if (remote != null) send(Message.obtain(null, CONFIG).apply { data = Bundle(copy) })
    }
    fun sendText(text: String) {
        if (text.isEmpty()) return
        ipc.post {
            val kcm = android.view.KeyCharacterMap.load(android.view.KeyCharacterMap.VIRTUAL_KEYBOARD)
            val events = kcm.getEvents(text.toCharArray())
            if (events != null && events.isNotEmpty()) {
                for (ev in events) {
                    runCatching { remote?.send(Message.obtain(null, KEY).apply { obj = KeyEvent(ev) }) }
                }
            }
        }
    }
    @Volatile private var lastLocSentMs: Long = 0L
    @Volatile private var latestLoc: android.location.Location? = null
    fun sendLocation(loc: android.location.Location) {
        val copy = android.location.Location(loc)
        latestLoc = copy
        val now = SystemClock.uptimeMillis()
        if (now - lastLocSentMs < 350L) return
        lastLocSentMs = now
        if (remote != null) send(Message.obtain(null, LOCATION).apply { obj = copy })
    }
    private fun sendFrame() { frame?.let { f -> send(Message.obtain(null, SURFACE).apply { data = Bundle(f) }) } }
    private fun send(m: Message) { ipc.post { runCatching { remote?.send(m) }.onFailure { Log.w(TAG, "send ${m.what} failed: ${it.message}") } } }
    private fun bind(app: Context, pkg: String): Boolean {
        unbind()
        val c = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                remote = Messenger(service).also { r -> runCatching { r.send(Message.obtain(null, HELLO).apply { replyTo = inbox }) } }
                sendFrame()
                latestConfig?.let { cfg -> send(Message.obtain(null, CONFIG).apply { data = Bundle(cfg) }) }
                latestLoc?.let { l -> send(Message.obtain(null, LOCATION).apply { obj = android.location.Location(l) }) }
                pendingQuery?.let { q -> send(Message.obtain(null, TEXT).apply { data = Bundle(q) }); pendingQuery = null }
            }
            override fun onServiceDisconnected(name: ComponentName) { remote = null; main.post { if (shown.value?.mirror != true) shown.value = null; target = 0 } }
        }
        val ok = runCatching { app.bindService(Intent(ACTION).setPackage(pkg), c, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT or Context.BIND_INCLUDE_CAPABILITIES) }.onFailure { Log.w(TAG, "bind $pkg failed: ${it.message}") }.getOrDefault(false)
        if (ok) { conn = c; bound = app } else Log.w(TAG, "bind $pkg refused")
        return ok
    }
    private fun unbind() { conn?.let { c -> runCatching { bound?.unbindService(c) } }; conn = null; bound = null; remote = null; frame = null }
}
