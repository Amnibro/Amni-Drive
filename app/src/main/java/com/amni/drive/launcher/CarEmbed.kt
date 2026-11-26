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
    private const val TAG = "Amni.Embed"
    data class Shown(val pkg: String, val label: String, val displayId: Int, val at: Long, val front: Boolean = true)
    val shown = MutableStateFlow<Shown?>(null)
    private var remote: Messenger? = null
    private var conn: ServiceConnection? = null
    private var bound: Context? = null
    private var forwarding = false
    private var frame: Bundle? = null
    private var area = IntArray(4)
    private val main = Handler(Looper.getMainLooper())
    private val inbox = Messenger(Handler(Looper.getMainLooper()) { m -> if (m.what == GONE && shown.value?.let { SystemClock.uptimeMillis() - it.at > 777 } == true) { Log.i(TAG, "app asked to go back home"); home() }; true })
    fun supports(ctx: Context, pkg: String): Boolean = runCatching { ctx.packageManager.resolveService(Intent(ACTION).setPackage(pkg), 0) != null }.getOrDefault(false)
    fun up(s: Shown?, displayId: Int?): Boolean = s != null && s.front && displayId != null && s.displayId == displayId
    fun on(displayId: Int?): Shown? = shown.value?.takeIf { up(it, displayId) }
    fun open(ctx: Context, entry: AppEntry, displayId: Int): Boolean {
        val cur = shown.value
        if (cur?.pkg != entry.packageName || cur.displayId != displayId || conn == null) { if (!bind(ctx.applicationContext, entry.packageName)) return false }
        shown.value = Shown(entry.packageName, entry.label, displayId, SystemClock.uptimeMillis())
        forwarding = false
        Log.i(TAG, "show ${entry.packageName} on display $displayId")
        return true
    }
    fun home() { shown.value = shown.value?.copy(front = false); forwarding = false }
    fun drop(displayId: Int) { if (shown.value?.displayId == displayId) { shown.value = null; forwarding = false; unbind() } }
    fun surface(s: Surface?, w: Int, h: Int, dpi: Int) { frame = Bundle().apply { putParcelable("s", s); putInt("w", w); putInt("h", h); putInt("dpi", dpi) }; sendFrame() }
    fun area(left: Int, top: Int, right: Int, bottom: Int) { area = intArrayOf(left, top, right, bottom) }
    fun route(root: View, ev: MotionEvent, displayId: Int): Boolean {
        on(displayId) ?: return root.dispatchTouchEvent(ev)
        val a = area
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) forwarding = ev.x >= a[0] && ev.x < a[2] && ev.y >= a[1] && ev.y < a[3]
        if (!forwarding) return root.dispatchTouchEvent(ev)
        MotionEvent.obtain(ev).let { c -> c.source = InputDevice.SOURCE_TOUCHSCREEN; c.offsetLocation(-a[0].toFloat(), -a[1].toFloat()); send(Message.obtain(null, TOUCH).apply { obj = c }); c.recycle() }
        return true
    }
    fun key(ev: KeyEvent, displayId: Int): Boolean = on(displayId)?.let { send(Message.obtain(null, KEY).apply { obj = KeyEvent(ev) }); true } ?: false
    private fun sendFrame() { frame?.let { f -> send(Message.obtain(null, SURFACE).apply { data = Bundle(f) }) } }
    private fun send(m: Message) { runCatching { remote?.send(m) }.onFailure { Log.w(TAG, "send ${m.what} failed: ${it.message}") } }
    private fun bind(app: Context, pkg: String): Boolean {
        unbind()
        val c = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) { remote = Messenger(service).also { r -> runCatching { r.send(Message.obtain(null, HELLO).apply { replyTo = inbox }) } }; sendFrame() }
            override fun onServiceDisconnected(name: ComponentName) { remote = null; main.post { shown.value = null; forwarding = false } }
        }
        val ok = runCatching { app.bindService(Intent(ACTION).setPackage(pkg), c, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT or Context.BIND_INCLUDE_CAPABILITIES) }.onFailure { Log.w(TAG, "bind $pkg failed: ${it.message}") }.getOrDefault(false)
        if (ok) { conn = c; bound = app } else Log.w(TAG, "bind $pkg refused")
        return ok
    }
    private fun unbind() { conn?.let { c -> runCatching { bound?.unbindService(c) } }; conn = null; bound = null; remote = null; frame = null }
}
