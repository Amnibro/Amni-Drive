package com.amni.drive.wire
import android.content.Context
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
class AoaSslSession(
private val rawIn: InputStream,
private val rawOut: OutputStream,
private val log: (String) -> Unit,
ctx: Context? = null
) {
private val engine: SSLEngine
private val appIn = ByteBuffer.allocate(64 * 1024)
private val appOut = ByteBuffer.allocate(64 * 1024)
private val netIn = ByteBuffer.allocate(64 * 1024)
private val netOut = ByteBuffer.allocate(64 * 1024)
init {
val built = buildContext(ctx, log)
engine = built.createSSLEngine().apply {
useClientMode = false
needClientAuth = false
wantClientAuth = true
try {
enabledProtocols = arrayOf("TLSv1.2")
} catch (_: Exception) {
try { enabledProtocols = arrayOf("TLSv1.2", "TLSv1.3") } catch (_: Exception) {}
}
try {
val prefer = enabledCipherSuites.filter {
it.contains("RSA", ignoreCase = true) ||
it.contains("AES", ignoreCase = true) ||
it.contains("ECDHE", ignoreCase = true) ||
it.contains("GCM", ignoreCase = true)
}.toTypedArray()
if (prefer.isNotEmpty()) enabledCipherSuites = prefer
} catch (_: Exception) {}
log("TLS engine protocols=${enabledProtocols.joinToString(",")} ciphers=${enabledCipherSuites.size}")
}
}
fun handshake(timeoutMs: Long = 15_000L): Boolean = handshakeRaw(timeoutMs)
fun handshakeAsClient(timeoutMs: Long = 8_000L): Boolean {
engine.useClientMode = true
engine.beginHandshake()
log("TLS CLIENT-mode experiment begin ${engine.handshakeStatus}")
return handshakeRaw(timeoutMs)
}
fun handshakeFramed(timeoutMs: Long = 15_000L): Boolean {
engine.beginHandshake()
var status = engine.handshakeStatus
log("TLS framed begin status=$status")
val t0 = System.currentTimeMillis()
val acc = ByteArrayOutputStream()
while (System.currentTimeMillis() - t0 < timeoutMs) {
when (status) {
SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
if (netIn.position() == 0) {
val bodies = AaAapVersion.drainSslHandshakeBodies(rawIn, acc, log, 800L)
if (bodies.isEmpty()) {
Thread.sleep(25)
status = engine.handshakeStatus
continue
}
for (b in bodies) {
if (netIn.remaining() < b.size) break
netIn.put(b)
log("TLS framed RX body ${b.size}")
}
}
netIn.flip()
val res = try {
engine.unwrap(netIn, appIn)
} catch (e: Exception) {
log("TLS framed unwrap ex ${e.javaClass.simpleName}: ${e.message}")
return false
}
netIn.compact()
log("TLS framed unwrap=${res.status} hs=${res.handshakeStatus}")
if (res.status == SSLEngineResult.Status.CLOSED) return false
status = res.handshakeStatus
if (status == SSLEngineResult.HandshakeStatus.NEED_TASK) status = runTasks()
}
SSLEngineResult.HandshakeStatus.NEED_WRAP -> {
if (!doWrap { AaAapVersion.writeSslHandshake(rawOut, it, log) }) return false
status = engine.handshakeStatus
if (status == SSLEngineResult.HandshakeStatus.NEED_TASK) status = runTasks()
}
SSLEngineResult.HandshakeStatus.NEED_TASK -> status = runTasks()
SSLEngineResult.HandshakeStatus.FINISHED, SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING -> {
log("TLS framed handshake done status=$status")
return true
}
else -> {
Thread.sleep(25)
status = engine.handshakeStatus
}
}
}
log("TLS framed handshake timeout last=$status")
return false
}
fun handshakeFramedBulk(io: AaByteIo, timeoutMs: Long = 15_000L, seedBodies: List<ByteArray> = emptyList()): Boolean {
fun normalizeTlsBody(raw: ByteArray): ByteArray {
if (raw.size >= 3 && (raw[0].toInt() and 0xFF) == 0x16) return raw
if (raw.size >= 5 && (raw[0].toInt() and 0xFF) == 0x00 && (raw[1].toInt() and 0xFF) == 0x03 && (raw[2].toInt() and 0xFF) == 0x16) {
log("TLS framed-bulk strip leftover msgId prefix")
return raw.copyOfRange(2, raw.size)
}
return raw
}
engine.beginHandshake()
var status = engine.handshakeStatus
log("TLS framed-bulk begin status=$status seedBodies=${seedBodies.size} protocols=${engine.enabledProtocols.joinToString(",")}")
val pending = ArrayDeque<ByteArray>()
for (b in seedBodies) {
if (b.isNotEmpty()) {
val n = normalizeTlsBody(b)
pending.add(n)
log("TLS framed-bulk seed body ${n.size} head=${AaAapVersion.hexHead(n, 16)}")
}
}
val t0 = System.currentTimeMillis()
while (System.currentTimeMillis() - t0 < timeoutMs) {
when (status) {
SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
if (pending.isNotEmpty() || netIn.position() == 0) {
if (pending.isEmpty()) {
val bodies = AaAapVersion.drainSslHandshakeBodies(io, log, 900L)
for (b in bodies) pending.add(normalizeTlsBody(b))
}
while (pending.isNotEmpty()) {
val b = pending.removeFirst()
if (netIn.remaining() < b.size) {
log("TLS framed-bulk netIn overflow ${b.size}")
pending.addFirst(b)
break
}
netIn.put(b)
log("TLS framed-bulk RX body ${b.size} head=${AaAapVersion.hexHead(b, 12)}")
}
}
if (netIn.position() == 0) {
Thread.sleep(25)
status = engine.handshakeStatus
continue
}
netIn.flip()
val res = try {
engine.unwrap(netIn, appIn)
} catch (e: Exception) {
val cause = generateSequence(e as Throwable?) { it.cause }.mapNotNull { it.message }.joinToString(" | ")
log("TLS framed-bulk unwrap ex ${e.javaClass.simpleName}: $cause remaining=${netIn.remaining()} head=${if (netIn.hasRemaining()) AaAapVersion.hexHead(ByteArray(netIn.remaining().coerceAtMost(24)).also { netIn.duplicate().get(it) }) else "empty"}")
return false
}
log("TLS framed-bulk unwrap=${res.status} hs=${res.handshakeStatus} c/p=${res.bytesConsumed()}/${res.bytesProduced()} netLeft=${netIn.remaining()}")
when (res.status) {
SSLEngineResult.Status.BUFFER_UNDERFLOW -> {
netIn.compact()
status = engine.handshakeStatus
continue
}
SSLEngineResult.Status.BUFFER_OVERFLOW -> {
log("TLS framed-bulk appIn overflow")
return false
}
SSLEngineResult.Status.CLOSED -> return false
else -> {}
}
netIn.compact()
status = res.handshakeStatus
if (status == SSLEngineResult.HandshakeStatus.NEED_TASK) status = runTasks()
}
SSLEngineResult.HandshakeStatus.NEED_WRAP -> {
if (!doWrap { AaAapVersion.writeSslHandshake(io, it, log) }) return false
status = engine.handshakeStatus
if (status == SSLEngineResult.HandshakeStatus.NEED_TASK) status = runTasks()
}
SSLEngineResult.HandshakeStatus.NEED_TASK -> status = runTasks()
SSLEngineResult.HandshakeStatus.FINISHED, SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING -> {
log("TLS framed-bulk done status=$status")
return true
}
else -> {
Thread.sleep(25)
status = engine.handshakeStatus
}
}
}
log("TLS framed-bulk timeout last=$status")
return false
}
private fun doWrap(send: (ByteArray) -> Unit): Boolean {
netOut.clear()
val res = try {
engine.wrap(ByteBuffer.allocate(0), netOut)
} catch (e: Exception) {
val cause = generateSequence(e as Throwable?) { it.cause }.mapNotNull { it.message }.joinToString(" | ")
log("TLS wrap ex ${e.javaClass.simpleName}: $cause")
return false
}
netOut.flip()
if (netOut.hasRemaining()) {
val bytes = ByteArray(netOut.remaining())
netOut.get(bytes)
send(bytes)
log("TLS TX wrap ${bytes.size} status=${res.status} hs=${res.handshakeStatus} head=${AaAapVersion.hexHead(bytes, 12)}")
} else {
log("TLS wrap empty status=${res.status} hs=${res.handshakeStatus}")
}
if (res.handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_TASK) runTasks()
return res.status != SSLEngineResult.Status.CLOSED
}
private fun handshakeRaw(timeoutMs: Long): Boolean {
engine.beginHandshake()
var status = engine.handshakeStatus
log("TLS raw begin status=$status")
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
log("TLS unwrap=${res.status} hs=${res.handshakeStatus}")
if (res.status == SSLEngineResult.Status.CLOSED) return false
status = res.handshakeStatus
if (status == SSLEngineResult.HandshakeStatus.NEED_TASK) status = runTasks()
}
SSLEngineResult.HandshakeStatus.NEED_WRAP -> {
if (!doWrap { rawOut.write(it); rawOut.flush() }) return false
status = engine.handshakeStatus
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
private val cryptoLock = Any()
private var decryptCarry = ByteArray(0)
fun encryptApp(plain: ByteArray): ByteArray = synchronized(cryptoLock) {
if (plain.isEmpty()) return ByteArray(0)
val src = ByteBuffer.wrap(plain)
val dst = ByteBuffer.allocate((plain.size + 1024).coerceAtLeast(engine.session.packetBufferSize.coerceAtLeast(18_432)))
val out = ByteArrayOutputStream(plain.size + 512)
while (src.hasRemaining()) {
dst.clear()
val res = engine.wrap(src, dst)
if (res.status == SSLEngineResult.Status.CLOSED) throw IllegalStateException("SSL closed on encrypt")
if (res.status == SSLEngineResult.Status.BUFFER_OVERFLOW) throw IllegalStateException("SSL encrypt overflow")
if (res.handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_TASK) runTasks()
dst.flip()
if (dst.hasRemaining()) {
val chunk = ByteArray(dst.remaining())
dst.get(chunk)
out.write(chunk)
}
if (res.bytesProduced() == 0 && res.bytesConsumed() == 0) break
}
out.toByteArray()
}
fun decryptApp(cipher: ByteArray): ByteArray = synchronized(cryptoLock) {
if (cipher.isEmpty() && decryptCarry.isEmpty()) return ByteArray(0)
val input = if (decryptCarry.isEmpty()) cipher else ByteArray(decryptCarry.size + cipher.size).also {
System.arraycopy(decryptCarry, 0, it, 0, decryptCarry.size)
System.arraycopy(cipher, 0, it, decryptCarry.size, cipher.size)
decryptCarry = ByteArray(0)
}
val src = ByteBuffer.wrap(input)
var dstCap = (input.size + 512).coerceAtLeast(engine.session.applicationBufferSize.coerceAtLeast(32_768))
var dst = ByteBuffer.allocate(dstCap)
val out = ByteArrayOutputStream(input.size)
while (src.hasRemaining()) {
dst.clear()
val res = engine.unwrap(src, dst)
if (res.status == SSLEngineResult.Status.CLOSED) throw IllegalStateException("SSL closed on decrypt")
if (res.status == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
val rem = ByteArray(src.remaining())
src.get(rem)
decryptCarry = rem
break
}
if (res.status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
dstCap *= 2
if (dstCap > 1_048_576) throw IllegalStateException("SSL decrypt overflow")
dst = ByteBuffer.allocate(dstCap)
continue
}
if (res.handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_TASK) runTasks()
dst.flip()
if (dst.hasRemaining()) {
val chunk = ByteArray(dst.remaining())
dst.get(chunk)
out.write(chunk)
}
if (res.bytesProduced() == 0 && res.bytesConsumed() == 0) break
}
out.toByteArray()
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
private const val ALIAS = "amni_aa"
private const val P12_ASSET = "amni_aa_host.p12"
private const val P12_PASS = "amni_aa_research"
fun buildContext(ctx: Context? = null, log: (String) -> Unit = {}): SSLContext {
val km = AaCertStore.loadKeyManagers(ctx, log) ?: softwareKeyManagers(ctx, log)
val tm = arrayOf<TrustManager>(object : X509TrustManager {
override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
})
val ctxSsl = try {
SSLContext.getInstance("TLSv1.2")
} catch (_: Exception) {
SSLContext.getInstance("TLS")
}
ctxSsl.init(km, tm, SecureRandom())
log("TLS context ready km=${km.size} provider=${ctxSsl.provider.name}")
return ctxSsl
}
private fun softwareKeyManagers(ctx: Context?, log: (String) -> Unit): Array<javax.net.ssl.KeyManager> {
return try {
val ks = KeyStore.getInstance("PKCS12")
val bytes = loadP12Bytes(ctx, log) ?: run {
log("TLS software PKCS12 missing")
return emptyArray()
}
ks.load(ByteArrayInputStream(bytes), P12_PASS.toCharArray())
val aliases = ks.aliases().toList()
log("TLS software PKCS12 aliases=${aliases.joinToString(",")} size=${bytes.size}")
val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
kmf.init(ks, P12_PASS.toCharArray())
val kms = kmf.keyManagers
log("TLS software KeyManagers=${kms.size}")
kms
} catch (e: Exception) {
log("TLS software KM fail ${e.javaClass.simpleName}: ${e.message}")
Log.w(TAG, "software KM", e)
emptyArray()
}
}
private fun loadP12Bytes(ctx: Context?, log: (String) -> Unit): ByteArray? {
if (ctx != null) {
try {
ctx.assets.open(P12_ASSET).use { ins ->
val all = ins.readBytes()
if (all.isNotEmpty()) {
log("TLS PKCS12 from assets ${all.size}B")
try {
val out = File(ctx.filesDir, P12_ASSET)
if (!out.exists() || out.length() != all.size.toLong()) {
FileOutputStream(out).use { it.write(all) }
}
} catch (_: Exception) {}
return all
}
}
} catch (e: Exception) {
log("TLS assets PKCS12 fail ${e.message}")
}
try {
val f = File(ctx.filesDir, P12_ASSET)
if (f.exists() && f.length() > 0) {
log("TLS PKCS12 from filesDir ${f.length()}B")
return f.readBytes()
}
} catch (_: Exception) {}
}
return null
}
fun fromFd(fis: FileInputStream, fos: FileOutputStream, log: (String) -> Unit, ctx: Context? = null) =
AoaSslSession(fis, fos, log, ctx)
fun fromBulk(ctx: Context, @Suppress("UNUSED_PARAMETER") bulk: AaByteIo, log: (String) -> Unit): AoaSslSession {
val dummyIn = object : InputStream() {
override fun read(): Int = -1
}
val dummyOut = object : OutputStream() {
override fun write(b: Int) {}
}
return AoaSslSession(dummyIn, dummyOut, log, ctx.applicationContext)
}
}
}
