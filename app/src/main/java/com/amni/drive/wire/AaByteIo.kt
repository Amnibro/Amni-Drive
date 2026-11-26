package com.amni.drive.wire

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Common bidirectional byte-stream transport for Android Auto Protocol (AAP) sessions.
 * Implemented by [AoaBulkIo] (wired USB AOA) and [TcpAaIo] (Wireless Android Auto TCP :5277).
 */
interface AaByteIo {
    fun write(bytes: ByteArray)
    fun waitDrain(timeoutMs: Long): ByteArray
    fun isAlive(): Boolean
    fun close()
}

/**
 * TCP transport for Wireless Android Auto (port 5277) and DHU-over-TCP connections.
 */
class TcpAaIo(
    private val socket: Socket,
    private val log: (String) -> Unit
) : AaByteIo {
    private val inp: InputStream
    private val out: OutputStream
    private val lock = Object()
    private val acc = ByteArrayOutputStream()
    private val running = AtomicBoolean(true)
    private val thread: Thread

    init {
        runCatching {
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.receiveBufferSize = 65_536
            socket.sendBufferSize = 65_536
        }
        inp = socket.getInputStream()
        out = socket.getOutputStream()
        thread = Thread({
            val tmp = ByteArray(16_384)
            var rxCount = 0
            while (running.get()) {
                val n = try {
                    inp.read(tmp)
                } catch (e: Exception) {
                    if (running.get()) log("TCP RX err ${e.message}")
                    break
                }
                if (n < 0) {
                    log("TCP RX EOF")
                    break
                }
                if (n == 0) continue
                synchronized(lock) {
                    acc.write(tmp, 0, n)
                    lock.notifyAll()
                }
                if (rxCount < 4) {
                    rxCount++
                    log("TCP RX #$rxCount $n head=${AaAapVersion.hexHead(tmp, n.coerceAtMost(24))}")
                }
            }
            running.set(false)
            synchronized(lock) { lock.notifyAll() }
        }, "waa-tcp-rx").also {
            it.isDaemon = true
            it.start()
        }
    }

    override fun write(bytes: ByteArray) {
        synchronized(out) {
            out.write(bytes)
            out.flush()
        }
    }

    override fun waitDrain(timeoutMs: Long): ByteArray {
        val t0 = System.currentTimeMillis()
        synchronized(lock) {
            while (acc.size() == 0 && running.get() && System.currentTimeMillis() - t0 < timeoutMs) {
                try {
                    lock.wait(40)
                } catch (_: InterruptedException) {
                    break
                }
            }
            if (acc.size() == 0) return ByteArray(0)
            val data = acc.toByteArray()
            acc.reset()
            return data
        }
    }

    override fun isAlive(): Boolean = running.get() && thread.isAlive && !socket.isClosed

    override fun close() {
        running.set(false)
        runCatching { inp.close() }
        runCatching { out.close() }
        runCatching { socket.close() }
        thread.interrupt()
        synchronized(lock) { lock.notifyAll() }
    }
}
