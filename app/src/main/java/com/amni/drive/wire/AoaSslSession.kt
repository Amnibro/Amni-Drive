package com.amni.drive.wire
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
/**
 * Research TLS server on AOA bulk duplex. Phone is AA SSL server; HU is client.
 * Self-signed cert will likely fail OEM validation — used to observe ClientHello / alerts.
 */
class AoaSslSession(
private val rawIn: InputStream,
private val rawOut: OutputStream,
private val log: (String) -> Unit
) {
private val engine: SSLEngine
private val appIn = ByteBuffer.allocate(64 * 1024)
private val appOut = ByteBuffer.allocate(64 * 1024)
private val netIn = ByteBuffer.allocate(64 * 1024)
private val netOut = ByteBuffer.allocate(64 * 1024)
init {
val ctx = buildContext()
engine = ctx.createSSLEngine().apply {
useClientMode = false
needClientAuth = false
wantClientAuth = true
}
}
fun handshake(timeoutMs: Long = 15_000L): Boolean {
engine.beginHandshake()
val t0 = System.currentTimeMillis()
var status = engine.handshakeStatus
log("TLS begin status=$status")
while (System.currentTimeMillis() - t0 < timeoutMs) {
when (status) {
SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
if (netIn.position() == 0) {
val n = readRaw(netIn)
if (n <= 0) {
Thread.sleep(20)
status = engine.handshakeStatus
continue
}
log("TLS RX raw $n for unwrap")
}
netIn.flip()
val res = engine.unwrap(netIn, appIn)
netIn.compact()
log("TLS unwrap=${res.status} hs=${res.handshakeStatus} bytes=${res.bytesConsumed()}/${res.bytesProduced()}")
if (res.status == SSLEngineResult.Status.CLOSED) return false
status = res.handshakeStatus
if (status == SSLEngineResult.HandshakeStatus.NEED_TASK) status = runTasks()
}
SSLEngineResult.HandshakeStatus.NEED_WRAP -> {
netOut.clear()
val res = engine.wrap(appOut, netOut)
netOut.flip()
if (netOut.hasRemaining()) {
val bytes = ByteArray(netOut.remaining())
netOut.get(bytes)
rawOut.write(bytes)
rawOut.flush()
log("TLS TX wrap ${bytes.size} status=${res.status} hs=${res.handshakeStatus}")
}
status = res.handshakeStatus
if (status == SSLEngineResult.HandshakeStatus.NEED_TASK) status = runTasks()
}
SSLEngineResult.HandshakeStatus.NEED_TASK -> status = runTasks()
SSLEngineResult.HandshakeStatus.FINISHED, SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING -> {
log("TLS handshake done status=$status")
return true
}
else -> {
log("TLS unexpected $status")
Thread.sleep(20)
status = engine.handshakeStatus
}
}
}
log("TLS handshake timeout")
return false
}
private fun runTasks(): SSLEngineResult.HandshakeStatus {
var t = engine.delegatedTask
while (t != null) {
t.run()
t = engine.delegatedTask
}
return engine.handshakeStatus
}
private fun readRaw(dst: ByteBuffer): Int {
val space = dst.remaining()
if (space <= 0) return 0
val tmp = ByteArray(space.coerceAtMost(8192))
val avail = try { rawIn.available() } catch (_: Exception) { 0 }
if (avail <= 0) return 0
val n = rawIn.read(tmp, 0, tmp.size.coerceAtMost(avail.coerceAtLeast(1)))
if (n > 0) dst.put(tmp, 0, n)
return n
}
companion object {
private const val TAG = "Amni.AoaSsl"
fun buildContext(): SSLContext {
val km = trustAllKeyManagers()
val tm = arrayOf<TrustManager>(object : X509TrustManager {
override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
})
val ctx = SSLContext.getInstance("TLS")
ctx.init(km, tm, SecureRandom())
return ctx
}
private fun trustAllKeyManagers(): Array<javax.net.ssl.KeyManager> {
return try {
val kpg = KeyPairGenerator.getInstance("RSA")
kpg.initialize(2048)
val pair = kpg.generateKeyPair()
// Minimal self-signed via AndroidKeyStore is heavy; use empty KM and let wrap fail soft if needed.
// Generate ephemeral keystore entry with BouncyCastle-free approach: KeyStore + dummy not available.
// Fall back to default algorithm KeyManager with in-memory KeyStore if cert available.
val ks = KeyStore.getInstance(KeyStore.getDefaultType())
ks.load(null, null)
// Without a cert chain SSLEngine server mode may fail — still surfaces ClientHello from peer.
val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
kmf.init(ks, CharArray(0))
kmf.keyManagers
} catch (e: Exception) {
Log.w(TAG, "KM init: ${e.message}")
emptyArray()
}
}
fun fromFd(fis: FileInputStream, fos: FileOutputStream, log: (String) -> Unit) =
AoaSslSession(fis, fos, log)
}
}
