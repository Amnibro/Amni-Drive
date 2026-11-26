package com.amni.drive.launcher
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import com.amni.drive.shell.ShellAngel
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
object CarApps {
    private const val TAG = "Amni.Apps"
    const val MAP = "map"
    const val MEDIA = "media"
    class Slot(val name: String) {
        var vd: VirtualDisplay? = null
        var pkg: String? = null
        var area: IntArray? = null
        var w = 0
        var h = 0
        var attached = false
    }
    private val slots = HashMap<String, Slot>()
    private val io = Executors.newSingleThreadExecutor()
    private var sock: LocalSocket? = null
    private var out: DataOutputStream? = null
    private var inp: DataInputStream? = null
    private var touched: Slot? = null
    val ready = MutableStateFlow(false)
    val shown = MutableStateFlow<Map<String, String>>(emptyMap())
    private fun slot(name: String) = slots.getOrPut(name) { Slot(name) }
    fun probe() { io.execute { link() } }
    private fun link(): Boolean {
        if (sock?.isConnected == true && out != null) {
            ready.value = true
            return true
        }
        return runCatching {
            LocalSocket().also {
                it.connect(LocalSocketAddress(ShellAngel.NAME, LocalSocketAddress.Namespace.ABSTRACT))
                sock = it
                out = DataOutputStream(it.outputStream.buffered())
                inp = DataInputStream(it.inputStream)
            }
            true
        }.getOrElse {
            sock = null
            out = null
            false
        }.also { ready.value = it }
    }
    private fun tx(block: (DataOutputStream) -> Unit) {
        io.execute {
            if (link()) runCatching {
                out?.let { block(it); it.flush() }
            }.onFailure {
                Log.w(TAG, "angel link lost: ${it.message}")
                runCatching { sock?.close() }
                sock = null
                out = null
                ready.value = false
            }
        }
    }
    fun open(ctx: Context, name: String, pkg: String) {
        val s = slot(name)
        val comp = ctx.packageManager.getLaunchIntentForPackage(pkg)?.component?.flattenToShortString() ?: return
        val fresh = s.pkg != pkg
        s.pkg = pkg
        shown.value = shown.value + (name to pkg)
        if (s.vd != null && s.attached) start(s, comp, fresh)
    }
    private fun start(s: Slot, comp: String, fresh: Boolean) {
        val id = s.vd?.display?.displayId ?: return
        tx { o ->
            o.writeInt(ShellAngel.START)
            o.writeInt(id)
            o.writeUTF(comp)
            o.writeBoolean(fresh)
            o.flush()
            Log.i(TAG, "start $comp on ${s.name}#$id: ${inp?.readUTF()?.trim()?.takeLast(160)}")
        }
    }
    fun close(name: String) { slot(name).pkg = null; shown.value = shown.value - name }
    fun surface(ctx: Context, name: String, surface: Surface?, w: Int, h: Int, dpi: Int) {
        val s = slot(name)
        if (surface == null || !surface.isValid || w <= 0 || h <= 0) {
            runCatching { s.vd?.surface = null }
            s.attached = false
            s.area = null
            return
        }
        val panelDpi = (dpi * 0.78f).toInt().coerceIn(180, 320)
        val wasDetached = s.vd == null || !s.attached
        runCatching {
            s.vd?.let {
                if (s.w != w || s.h != h) it.resize(w, h, panelDpi)
                it.surface = surface
            } ?: run {
                s.vd = (ctx.applicationContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
                    .createVirtualDisplay(
                        "AmniDrivePanel-$name",
                        w,
                        h,
                        panelDpi,
                        surface,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                    )
            }
        }.onFailure { Log.w(TAG, "panel $name failed: ${it.message}") }
        s.w = w
        s.h = h
        s.attached = s.vd != null
        Log.i(TAG, "panel $name ${w}x$h@$panelDpi display=${s.vd?.display?.displayId}")
        if (wasDetached && s.attached) {
            s.pkg?.let { p ->
                ctx.packageManager.getLaunchIntentForPackage(p)?.component?.flattenToShortString()?.let {
                    start(s, it, true)
                }
            }
        }
    }
    fun area(name: String, a: IntArray?) { slot(name).area = a }
    fun pick(ev: MotionEvent): Boolean {
        touched = slots.values.firstOrNull { s ->
            s.area?.let { a -> ev.x >= a[0] && ev.x < a[2] && ev.y >= a[1] && ev.y < a[3] } == true &&
                s.vd != null && s.attached && ready.value
        }
        return touched != null
    }
    fun touch(ev: MotionEvent) {
        val s = touched ?: return
        val a = s.area ?: return
        val id = s.vd?.display?.displayId ?: return
        val action = ev.actionMasked
        val x = ev.x - a[0]
        val y = ev.y - a[1]
        tx { o ->
            o.writeInt(ShellAngel.TOUCH)
            o.writeInt(id)
            o.writeInt(action)
            o.writeFloat(x)
            o.writeFloat(y)
        }
    }
    fun key(name: String, ev: KeyEvent): Boolean {
        val id = slot(name).vd?.display?.displayId ?: return false
        if (!ready.value) return false
        val code = ev.keyCode
        val action = ev.action
        tx { o ->
            o.writeInt(ShellAngel.KEY)
            o.writeInt(id)
            o.writeInt(code)
            o.writeInt(action)
        }
        return true
    }
    fun drop() {
        slots.values.forEach { s ->
            runCatching { s.vd?.release() }
            s.vd = null
            s.area = null
            s.attached = false
        }
    }
}
