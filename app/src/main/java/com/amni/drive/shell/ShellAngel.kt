package com.amni.drive.shell
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import java.io.DataInputStream
import java.io.DataOutputStream
object ShellAngel {
    const val NAME = "amni-drive-shell"
    const val START = 1
    const val TOUCH = 2
    const val KEY = 3
    const val STOP = 4
    private val inject by lazy { runCatching { Class.forName("android.hardware.input.InputManagerGlobal").let { c -> c.getMethod("getInstance").invoke(null) to c.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType) } }.getOrElse { Class.forName("android.hardware.input.InputManager").let { c -> c.getMethod("getInstance").invoke(null) to c.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType) } } }
    private val setDisplay by lazy { InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType) }
    private fun send(ev: InputEvent, display: Int) { runCatching { setDisplay.invoke(ev, display); inject.second.invoke(inject.first, ev, 0) }.onFailure { System.err.println("inject failed: $it") } }
    private fun sh(vararg cmd: String): String = runCatching { ProcessBuilder(*cmd).redirectErrorStream(true).start().let { p -> p.inputStream.bufferedReader().readText().also { p.waitFor() } } }.getOrElse { it.toString() }
    private fun serve(s: LocalSocket, uid: Int) {
        if (s.peerCredentials.uid != uid) { s.close(); return }
        val i = DataInputStream(s.inputStream)
        val o = DataOutputStream(s.outputStream)
        var down = 0L
        runCatching {
            while (true) when (i.readInt()) {
                START -> { val display = i.readInt(); val comp = i.readUTF(); val fresh = i.readBoolean(); val out = sh(*(listOf("am", "start", "--display", "$display", "--windowingMode", "1", "-n", comp, "-f", "0x10000000") + (if (fresh) listOf("-S") else emptyList())).toTypedArray()); synchronized(o) { o.writeUTF(out.takeLast(300)); o.flush() } }
                TOUCH -> { val display = i.readInt(); val action = i.readInt(); val x = i.readFloat(); val y = i.readFloat(); val now = SystemClock.uptimeMillis(); if (action == MotionEvent.ACTION_DOWN) down = now; MotionEvent.obtain(down, now, action, x, y, 0).let { ev -> ev.source = InputDevice.SOURCE_TOUCHSCREEN; send(ev, display); ev.recycle() } }
                KEY -> { val display = i.readInt(); val code = i.readInt(); val action = i.readInt(); val now = SystemClock.uptimeMillis(); send(KeyEvent(now, now, action, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD), display) }
                STOP -> sh("am", "force-stop", i.readUTF())
            }
        }
        runCatching { s.close() }
    }
    @JvmStatic
    fun main(args: Array<String>) {
        val uid = args.firstOrNull()?.toIntOrNull() ?: return System.err.println("usage: ShellAngel <uid>")
        val server = LocalServerSocket(NAME)
        println("amni-drive shell angel up for uid $uid")
        while (true) server.accept().let { s -> Thread { serve(s, uid) }.start() }
    }
}
