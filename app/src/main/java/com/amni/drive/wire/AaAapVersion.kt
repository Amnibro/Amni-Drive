package com.amni.drive.wire
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
object AaAapVersion {
const val CHANNEL_CONTROL = 0
const val MSG_VERSION_REQUEST = 0x0001
const val MSG_VERSION_RESPONSE = 0x0002
const val MSG_SSL_HANDSHAKE = 0x0003
const val MSG_AUTH_COMPLETE = 0x0004
const val FRAME_MIDDLE = 0
const val FRAME_FIRST = 1
const val FRAME_LAST = 2
const val FRAME_BULK = FRAME_FIRST or FRAME_LAST
const val ENC_PLAIN = 0
const val ENC_ENCRYPTED = 1 shl 3
const val TYPE_SPECIFIC = 0
const val TYPE_CONTROL = 1 shl 2
const val MAJOR = 1
const val MINOR = 1
const val STATUS_OK = 0
val PROBE_MINORS = intArrayOf(1, 4, 5, 6, 7)
data class Parsed(val channel: Int, val flags: Int, val msgId: Int, val payload: ByteArray, val frameLen: Int = 4 + 2 + payload.size)
data class NegotiateResult(val ok: Boolean, val sslBodies: List<ByteArray> = emptyList())
fun frameFlags(bulk: Boolean = true, encrypted: Boolean = false, control: Boolean = false): Int {
val ft = if (bulk) FRAME_BULK else FRAME_FIRST
val enc = if (encrypted) ENC_ENCRYPTED else ENC_PLAIN
val mt = if (control) TYPE_CONTROL else TYPE_SPECIFIC
return ft or enc or mt
}
fun encodeFrame(channel: Int, flags: Int, payload: ByteArray): ByteArray {
val size = payload.size
val out = ByteArray(2 + 2 + size)
out[0] = (channel and 0xFF).toByte()
out[1] = (flags and 0xFF).toByte()
out[2] = ((size ushr 8) and 0xFF).toByte()
out[3] = (size and 0xFF).toByte()
System.arraycopy(payload, 0, out, 4, size)
return out
}
fun encodeMessage(channel: Int, msgId: Int, body: ByteArray = ByteArray(0), flags: Int = frameFlags()): ByteArray {
val payload = ByteArray(2 + body.size)
payload[0] = ((msgId ushr 8) and 0xFF).toByte()
payload[1] = (msgId and 0xFF).toByte()
System.arraycopy(body, 0, payload, 2, body.size)
return encodeFrame(channel, flags, payload)
}
fun cappedVersionReply(major: Int, minor: Int): Pair<Int, Int> =
if (major < 1 || (major == 1 && minor <= 7)) major to minor else 6 to 2
fun versionResponse(major: Int = MAJOR, minor: Int = MINOR, status: Int = STATUS_OK): ByteArray {
val body = ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN)
body.putShort(major.toShort())
body.putShort(minor.toShort())
body.putShort(status.toShort())
return encodeMessage(CHANNEL_CONTROL, MSG_VERSION_RESPONSE, body.array())
}
fun versionRequest(major: Int = MAJOR, minor: Int = MINOR): ByteArray {
val body = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
body.putShort(major.toShort())
body.putShort(minor.toShort())
return encodeMessage(CHANNEL_CONTROL, MSG_VERSION_REQUEST, body.array())
}
fun sslHandshakeFrame(tlsRecord: ByteArray): ByteArray =
encodeMessage(CHANNEL_CONTROL, MSG_SSL_HANDSHAKE, tlsRecord)
fun tryParse(bytes: ByteArray, offset: Int = 0): Parsed? {
if (bytes.size - offset < 4) return null
val channel = bytes[offset].toInt() and 0xFF
val flags = bytes[offset + 1].toInt() and 0xFF
val size = ((bytes[offset + 2].toInt() and 0xFF) shl 8) or (bytes[offset + 3].toInt() and 0xFF)
val fragType = flags and 3
val hdrLen = if (fragType == FRAME_FIRST) 8 else 4
if (size < 0 || size > 1_000_000) return null
if (bytes.size - offset < hdrLen + size) return null
val payload = bytes.copyOfRange(offset + hdrLen, offset + hdrLen + size)
val hasMsgId = fragType == FRAME_BULK || fragType == FRAME_FIRST
if (hasMsgId && payload.size < 2) return null
val msgId = if (hasMsgId) (((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)) else MSG_SSL_HANDSHAKE
val body = if (hasMsgId) (if (payload.size > 2) payload.copyOfRange(2, payload.size) else ByteArray(0)) else payload
return Parsed(channel, flags, msgId, body, hdrLen + size)
}
fun hexHead(b: ByteArray, n: Int = 24): String =
b.take(n.coerceAtMost(b.size)).joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }
fun readAvailable(inp: InputStream, acc: ByteArrayOutputStream, buf: ByteArray, log: (String) -> Unit): Int {
val avail = try { inp.available() } catch (_: Exception) { 0 }
if (avail <= 0) return 0
val n = try { inp.read(buf, 0, minOf(buf.size, avail)) } catch (_: Exception) { -1 }
if (n > 0) {
acc.write(buf, 0, n)
log("AAP RX $n head=${hexHead(buf, n.coerceAtMost(24))}")
}
return n
}
fun scanFrames(all: ByteArray, log: (String) -> Unit, tag: String): List<Parsed> {
val list = mutableListOf<Parsed>()
var off = 0
while (off < all.size) {
val p = tryParse(all, off) ?: break
log("$tag ch=${p.channel} flags=0x${p.flags.toString(16)} msg=0x${p.msgId.toString(16)} body=${p.payload.size}")
list.add(p)
off += p.frameLen
}
return list
}
fun interesting(frames: List<Parsed>): Boolean =
frames.any {
it.msgId == MSG_VERSION_REQUEST || it.msgId == MSG_SSL_HANDSHAKE || it.msgId == MSG_VERSION_RESPONSE
}
fun isHuSignal(frames: List<Parsed>): Boolean =
frames.any { it.msgId == 0x00FF || (it.flags and ENC_ENCRYPTED) != 0 }
fun versionResponseFlags(major: Int, minor: Int, control: Boolean): ByteArray {
val body = ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN)
body.putShort(major.toShort())
body.putShort(minor.toShort())
body.putShort(STATUS_OK.toShort())
return encodeMessage(CHANNEL_CONTROL, MSG_VERSION_RESPONSE, body.array(), frameFlags(control = control))
}
fun versionRequestFlags(major: Int, minor: Int, control: Boolean): ByteArray {
val body = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
body.putShort(major.toShort())
body.putShort(minor.toShort())
return encodeMessage(CHANNEL_CONTROL, MSG_VERSION_REQUEST, body.array(), frameFlags(control = control))
}
fun sslBodiesOf(frames: List<Parsed>): List<ByteArray> =
frames.filter { it.msgId == MSG_SSL_HANDSHAKE && it.payload.isNotEmpty() }.map { it.payload }
fun negotiateBulk(io: AaByteIo, log: (String) -> Unit, waitMs: Long = 10_000L, seed: ByteArray = ByteArray(0)): NegotiateResult {
var gotAny = seed.isNotEmpty()
val hold = ByteArrayOutputStream()
var loggedLen = 0
if (seed.isNotEmpty()) {
hold.write(seed)
log("AAP seed ${seed.size}B into negotiate")
}
fun drain(ms: Long): List<Parsed> {
val t0 = System.currentTimeMillis()
var last: List<Parsed> = emptyList()
while (System.currentTimeMillis() - t0 < ms) {
val chunk = io.waitDrain(80)
if (chunk.isNotEmpty()) {
gotAny = true
hold.write(chunk)
}
val all = hold.toByteArray()
if (all.isEmpty()) continue
if (all.size > loggedLen) {
last = scanFrames(all, log, "AAP")
loggedLen = all.size
} else {
last = scanFrames(all, { }, "AAP")
}
if (interesting(last) || isHuSignal(last)) return last
}
return last
}
fun resetHold() { hold.reset(); loggedLen = 0 }
fun afterVersionOk(tag: String): NegotiateResult {
log("AAP wait SSL_HANDSHAKE after $tag")
val more = drain((waitMs / 5).coerceAtLeast(1_200L))
val ssl = sslBodiesOf(more)
log("AAP post-version sslBodies=${ssl.size} gotAny=$gotAny")
return NegotiateResult(true, ssl)
}
val slice = (waitMs / 6).coerceAtLeast(900L)
log("AAP bulk phase1 wait VersionRequest ${slice}ms (blocking bulk RX)")
var frames = drain(slice)
val req = frames.firstOrNull { it.msgId == MSG_VERSION_REQUEST }
if (req != null) {
val maj = if (req.payload.size >= 2) ((req.payload[0].toInt() and 0xFF) shl 8) or (req.payload[1].toInt() and 0xFF) else MAJOR
val min = if (req.payload.size >= 4) ((req.payload[2].toInt() and 0xFF) shl 8) or (req.payload[3].toInt() and 0xFF) else MINOR
val (rMaj, rMin) = cappedVersionReply(maj, min)
val rsp = versionResponse(rMaj, rMin, STATUS_OK)
io.write(rsp)
log("AAP TX VersionResponse (reply) ${rsp.size}B hex=${hexHead(rsp)} hu=$maj.$min reply=$rMaj.$rMin")
resetHold()
return afterVersionOk("reply $rMaj.$rMin")
}
val earlySsl = sslBodiesOf(frames)
if (earlySsl.isNotEmpty()) {
log("AAP already have SSL_HANDSHAKE without version reply bodies=${earlySsl.size}")
return NegotiateResult(true, earlySsl)
}
for (control in booleanArrayOf(false, true)) {
val tag = if (control) "ctl" else "spec"
for (minor in PROBE_MINORS) {
val reqTx = versionRequestFlags(MAJOR, minor, control)
io.write(reqTx)
log("AAP TX VersionRequest phone-first 1.$minor $tag ${reqTx.size}B hex=${hexHead(reqTx)}")
resetHold()
frames = drain(slice)
if (frames.any { it.msgId == MSG_VERSION_REQUEST }) {
val r = frames.first { it.msgId == MSG_VERSION_REQUEST }
val maj = if (r.payload.size >= 2) ((r.payload[0].toInt() and 0xFF) shl 8) or (r.payload[1].toInt() and 0xFF) else MAJOR
val min = if (r.payload.size >= 4) ((r.payload[2].toInt() and 0xFF) shl 8) or (r.payload[3].toInt() and 0xFF) else minor
val (rMaj, rMin) = cappedVersionReply(maj, min)
val rsp = versionResponseFlags(rMaj, rMin, control)
io.write(rsp)
log("AAP TX VersionResponse after phone-first ${rsp.size}B hex=${hexHead(rsp)} hu=$maj.$min reply=$rMaj.$rMin")
resetHold()
return afterVersionOk("phone-first $rMaj.$rMin $tag")
}
val ssl = sslBodiesOf(frames)
if (ssl.isNotEmpty()) {
log("AAP phone-first 1.$minor $tag got SSL_HANDSHAKE")
return NegotiateResult(true, ssl)
}
if (isHuSignal(frames)) {
log("AAP HU signal after VersionRequest 1.$minor $tag — try VersionResponse")
val rsp = versionResponseFlags(MAJOR, minor, control)
io.write(rsp)
log("AAP TX VersionResponse after signal ${rsp.size}B hex=${hexHead(rsp)}")
resetHold()
frames = drain(slice)
val ssl2 = sslBodiesOf(frames)
if (ssl2.isNotEmpty() || interesting(frames)) {
log("AAP after-signal interesting ssl=${ssl2.size} gotAny=$gotAny")
return NegotiateResult(true, ssl2)
}
}
}
for (minor in PROBE_MINORS) {
val rsp = versionResponseFlags(MAJOR, minor, control)
io.write(rsp)
log("AAP TX VersionResponse probe 1.$minor $tag ${rsp.size}B hex=${hexHead(rsp)}")
resetHold()
frames = drain(slice)
val ssl = sslBodiesOf(frames)
if (ssl.isNotEmpty() || interesting(frames)) {
log("AAP probe rsp 1.$minor $tag ssl=${ssl.size} gotAny=$gotAny")
return NegotiateResult(true, ssl)
}
}
}
log("AAP negotiate exhausted gotAny=$gotAny bulkAlive=${io.isAlive()}")
return NegotiateResult(false)
}
fun negotiate(inp: InputStream, out: OutputStream, log: (String) -> Unit, waitMs: Long = 8_000L): Boolean {
var gotAny = false
val buf = ByteArray(8192)
val acc = ByteArrayOutputStream()
fun drain(ms: Long): List<Parsed> {
val t0 = System.currentTimeMillis()
while (System.currentTimeMillis() - t0 < ms) {
if (readAvailable(inp, acc, buf, log) > 0) gotAny = true
val frames = scanFrames(acc.toByteArray(), log, "AAP")
if (interesting(frames)) return frames
Thread.sleep(40)
}
return scanFrames(acc.toByteArray(), log, "AAP")
}
val slice = (waitMs / 5).coerceAtLeast(800L)
log("AAP phase1 wait VersionRequest ${slice}ms (available-poll fallback)")
var frames = drain(slice)
val req = frames.firstOrNull { it.msgId == MSG_VERSION_REQUEST }
if (req != null) {
val rsp = versionResponse()
out.write(rsp)
out.flush()
log("AAP TX VersionResponse (reply) ${rsp.size}B hex=${hexHead(rsp)}")
return true
}
for (minor in PROBE_MINORS) {
val rsp = versionResponse(MAJOR, minor, STATUS_OK)
out.write(rsp)
out.flush()
log("AAP TX VersionResponse probe 1.$minor ${rsp.size}B hex=${hexHead(rsp)}")
acc.reset()
frames = drain(slice)
if (interesting(frames)) return true
}
val reqTx = versionRequest(MAJOR, MINOR)
out.write(reqTx)
out.flush()
log("AAP TX VersionRequest (phone-first) ${reqTx.size}B hex=${hexHead(reqTx)}")
acc.reset()
frames = drain(slice)
log("AAP negotiate fallback exhausted gotAny=$gotAny")
return interesting(frames)
}
fun drainSslHandshakeBodies(io: AaByteIo, log: (String) -> Unit, waitMs: Long): List<ByteArray> {
val out = mutableListOf<ByteArray>()
val hold = ByteArrayOutputStream()
val t0 = System.currentTimeMillis()
while (System.currentTimeMillis() - t0 < waitMs) {
val chunk = io.waitDrain(100)
if (chunk.isNotEmpty()) hold.write(chunk)
val all = hold.toByteArray()
var off = 0
var consumed = 0
while (off < all.size) {
val p = tryParse(all, off) ?: break
off += p.frameLen
consumed = off
if (p.msgId == MSG_SSL_HANDSHAKE && p.payload.isNotEmpty()) {
out.add(p.payload)
log("AAP SSL_HANDSHAKE body ${p.payload.size}B")
}
}
if (consumed > 0) {
hold.reset()
if (consumed < all.size) hold.write(all, consumed, all.size - consumed)
}
if (out.isNotEmpty()) return out
}
return out
}
fun drainSslHandshakeBodies(inp: InputStream, acc: ByteArrayOutputStream, log: (String) -> Unit, waitMs: Long): List<ByteArray> {
val out = mutableListOf<ByteArray>()
val buf = ByteArray(8192)
val t0 = System.currentTimeMillis()
while (System.currentTimeMillis() - t0 < waitMs) {
readAvailable(inp, acc, buf, log)
val all = acc.toByteArray()
var off = 0
var consumed = 0
while (off < all.size) {
val p = tryParse(all, off) ?: break
off += p.frameLen
consumed = off
if (p.msgId == MSG_SSL_HANDSHAKE && p.payload.isNotEmpty()) {
out.add(p.payload)
log("AAP SSL_HANDSHAKE body ${p.payload.size}B")
}
}
if (consumed > 0 && consumed < all.size) {
acc.reset()
acc.write(all, consumed, all.size - consumed)
} else if (consumed == all.size) acc.reset()
if (out.isNotEmpty()) return out
Thread.sleep(30)
}
return out
}
fun writeSslHandshake(out: OutputStream, tlsBytes: ByteArray, log: (String) -> Unit) {
if (tlsBytes.isEmpty()) return
val maxChunk = 16_000
var i = 0
while (i < tlsBytes.size) {
val n = minOf(maxChunk, tlsBytes.size - i)
val chunk = tlsBytes.copyOfRange(i, i + n)
val frame = sslHandshakeFrame(chunk)
out.write(frame)
out.flush()
log("AAP TX SSL_HANDSHAKE ${frame.size}B tls=$n hex=${hexHead(frame)}")
i += n
}
}
fun writeSslHandshake(io: AaByteIo, tlsBytes: ByteArray, log: (String) -> Unit) {
if (tlsBytes.isEmpty()) return
val maxChunk = 16_000
var i = 0
while (i < tlsBytes.size) {
val n = minOf(maxChunk, tlsBytes.size - i)
val chunk = tlsBytes.copyOfRange(i, i + n)
val frame = sslHandshakeFrame(chunk)
io.write(frame)
log("AAP TX SSL_HANDSHAKE ${frame.size}B tls=$n hex=${hexHead(frame)}")
i += n
}
}
}
