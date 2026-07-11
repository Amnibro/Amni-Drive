package com.amni.drive.wire
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
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
import javax.security.auth.x500.X500Principal
/**
 * Research TLS server on AOA bulk duplex. Phone is AA SSL server; HU is client.
 * Uses AndroidKeyStore self-signed identity so SSLEngine can WRAP ServerHello.
 * OEM HU will still reject unknown CA — we log ClientHello / alerts for reverse-eng.
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
val ctx = buildContext(log)
engine = ctx.createSSLEngine().apply {
useClientMode = false
needClientAuth = false
wantClientAuth = false
}
}
fun handshake(timeoutMs: Long = 15_000L): Boolean {
engine.beginHandshake()
var status = engine.handshakeStatus
log("TLS begin status=$status")
val t0 = System.currentTimeMillis()
while (System.currentTimeMillis() - t0 < timeoutMs) {
when (status) {
SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
if (netIn.position() == 0) {
val n = readRaw(netIn)
if (n <= 0) {
Thread.sleep(25)
status = engine.handshakeStatus
continue
}
log("TLS RX raw $n for unwrap")
}
netIn.flip()
val res = try {
engine.unwrap(netIn, appIn)
} catch (e: Exception) {
log("TLS unwrap ex ${e.javaClass.simpleName}: ${e.message}")
return false
}
netIn.compact()
log("TLS unwrap=${res.status} hs=${res.handshakeStatus} c/p=${res.bytesConsumed()}/${res.bytesProduced()}")
if (res.status == SSLEngineResult.Status.CLOSED) return false
status = res.handshakeStatus
if (status == SSLEngineResult.HandshakeStatus.NEED_TASK) status = runTasks()
}
SSLEngineResult.HandshakeStatus.NEED_WRAP -> {
netOut.clear()
val res = try {
engine.wrap(appOut, netOut)
} catch (e: Exception) {
log("TLS wrap ex ${e.javaClass.simpleName}: ${e.message}")
return false
}
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
Thread.sleep(25)
status = engine.handshakeStatus
}
}
}
log("TLS handshake timeout last=$status")
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
private const val ALIAS = "amni_aa_tls_v1"
fun buildContext(log: (String) -> Unit = {}): SSLContext {
val km = keyManagers(log)
val tm = arrayOf<TrustManager>(object : X509TrustManager {
override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
})
val ctx = SSLContext.getInstance("TLS")
ctx.init(km, tm, SecureRandom())
log("TLS context ready km=${km.size}")
return ctx
}
private fun keyManagers(log: (String) -> Unit): Array<javax.net.ssl.KeyManager> {
return try {
if (Build.VERSION.SDK_INT >= 23) {
ensureAndroidKey(log)
val ks = KeyStore.getInstance("AndroidKeyStore")
ks.load(null)
val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
kmf.init(ks, null)
kmf.keyManagers
} else {
emptyArray()
}
} catch (e: Exception) {
log("KM fail ${e.message}")
Log.w(TAG, "KM", e)
emptyArray()
}
}
private fun ensureAndroidKey(log: (String) -> Unit) {
val ks = KeyStore.getInstance("AndroidKeyStore")
ks.load(null)
if (ks.containsAlias(ALIAS)) {
log("TLS key alias exists")
return
}
val start = Date()
val end = Date(start.time + 365L * 24 * 60 * 60 * 1000)
val spec = KeyGenParameterSpec.Builder(
ALIAS,
KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
)
.setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
.setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
.setCertificateSubject(X500Principal("CN=AmniDrive-AA-Research"))
.setCertificateSerialNumber(BigInteger.valueOf(System.currentTimeMillis()))
.setCertificateNotBefore(start)
.setCertificateNotAfter(end)
.setKeySize(2048)
.build()
val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
kpg.initialize(spec)
kpg.generateKeyPair()
log("TLS AndroidKeyStore key generated alias=$ALIAS")
}
fun fromFd(fis: FileInputStream, fos: FileOutputStream, log: (String) -> Unit) =
AoaSslSession(fis, fos, log)
}
}
