package com.amni.drive.wire
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
class AoaBulkIo(
pfd: ParcelFileDescriptor,
private val log: (String) -> Unit
) : AaByteIo {
private val fis = FileInputStream(pfd.fileDescriptor)
private val fos = FileOutputStream(pfd.fileDescriptor)
private val lock = Object()
private val acc = ByteArrayOutputStream()
private val running = AtomicBoolean(true)
private val thread = Thread({
val tmp = ByteArray(16_384)
var rxCount = 0
while (running.get()) {
val n = try { fis.read(tmp) } catch (e: Exception) {
log("BULK RX err ${e.message}")
break
}
if (n < 0) {
log("BULK RX EOF")
break
}
if (n == 0) continue
synchronized(lock) {
acc.write(tmp, 0, n)
lock.notifyAll()
}
if (rxCount < 4) {
rxCount++
log("BULK RX #$rxCount $n head=${AaAapVersion.hexHead(tmp, n.coerceAtMost(24))}")
}
}
running.set(false)
synchronized(lock) { lock.notifyAll() }
}, "aoa-bulk-rx").also {
it.isDaemon = true
it.start()
}
override fun write(bytes: ByteArray) {
synchronized(fos) {
fos.write(bytes)
fos.flush()
}
}
override fun waitDrain(timeoutMs: Long): ByteArray {
val t0 = System.currentTimeMillis()
synchronized(lock) {
while (acc.size() == 0 && running.get() && System.currentTimeMillis() - t0 < timeoutMs) {
try { lock.wait(40) } catch (_: InterruptedException) { break }
}
if (acc.size() == 0) return ByteArray(0)
val out = acc.toByteArray()
acc.reset()
return out
}
}
fun peekSize(): Int = synchronized(lock) { acc.size() }
override fun isAlive(): Boolean = running.get() && thread.isAlive
override fun close() {
running.set(false)
try { fis.close() } catch (_: Exception) {}
try { fos.close() } catch (_: Exception) {}
thread.interrupt()
}
}
