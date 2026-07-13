package com.amni.drive.wire
import java.io.ByteArrayOutputStream
object AaPostTls {
const val MSG_AUTH_COMPLETE = 0x0004
const val MSG_SERVICE_DISCOVERY_REQUEST = 0x0005
const val MSG_SERVICE_DISCOVERY_RESPONSE = 0x0006
const val MSG_CHANNEL_OPEN_REQUEST = 0x0007
const val MSG_CHANNEL_OPEN_RESPONSE = 0x0008
const val MSG_PING_REQUEST = 0x000b
const val MSG_PING_RESPONSE = 0x000c
const val MSG_NAV_FOCUS_REQUEST = 0x000d
const val MSG_NAV_FOCUS_RESPONSE = 0x000e
const val MSG_SHUTDOWN_REQUEST = 0x000f
const val MSG_SHUTDOWN_RESPONSE = 0x0010
const val MSG_AUDIO_FOCUS_REQUEST = 0x0012
const val MSG_AUDIO_FOCUS_RESPONSE = 0x0013
const val MSG_UNKNOWN_FF = 0x00ff
const val CH_CONTROL = 0
const val CH_SENSOR = 1
const val CH_VIDEO = 2
const val CH_INPUT = 3
const val STATUS_OK = 0
data class Result(val discovery: Boolean, val videoChannel: Int, val rxFrames: Int, val notes: String)
data class ChanInfo(val id: Int, val kind: String)
fun authCompleteBody(): ByteArray = AaPb.u32(1, STATUS_OK)
fun serviceDiscoveryRequestBody(
name: String = "AmniDrive",
brand: String = "Amni"
): ByteArray = AaPb.cat(AaPb.str(4, name), AaPb.str(5, brand))
fun channelOpenRequestBody(channelId: Int, priority: Int = 0): ByteArray =
AaPb.cat(AaPb.u32(1, priority), AaPb.u32(2, channelId))
fun parseStatus(body: ByteArray): Int {
var i = 0
while (i < body.size) {
val b = body[i++].toInt() and 0xFF
val field = b shr 3
val wire = b and 7
when (wire) {
0 -> {
var n = 0L
var shift = 0
while (i < body.size) {
val x = body[i++].toInt() and 0xFF
n = n or ((x and 0x7F).toLong() shl shift)
if (x and 0x80 == 0) break
shift += 7
}
if (field == 1) return n.toInt()
}
2 -> {
var len = 0
var shift = 0
while (i < body.size) {
val x = body[i++].toInt() and 0xFF
len = len or ((x and 0x7F) shl shift)
if (x and 0x80 == 0) break
shift += 7
}
i += len
}
1 -> i += 8
5 -> i += 4
else -> return -1
}
}
return -1
}
fun parseChannelId(body: ByteArray): Int {
var i = 0
while (i < body.size) {
val b = body[i++].toInt() and 0xFF
val field = b shr 3
val wire = b and 7
when (wire) {
0 -> {
var n = 0L
var shift = 0
while (i < body.size) {
val x = body[i++].toInt() and 0xFF
n = n or ((x and 0x7F).toLong() shl shift)
if (x and 0x80 == 0) break
shift += 7
}
if (field == 2) return n.toInt()
}
2 -> {
var len = 0
var shift = 0
while (i < body.size) {
val x = body[i++].toInt() and 0xFF
len = len or ((x and 0x7F) shl shift)
if (x and 0x80 == 0) break
shift += 7
}
i += len
}
1 -> i += 8
5 -> i += 4
else -> return -1
}
}
return -1
}
fun parseChannels(body: ByteArray): List<ChanInfo> {
val out = ArrayList<ChanInfo>()
var i = 0
while (i < body.size) {
val b = body[i++].toInt() and 0xFF
val field = b shr 3
val wire = b and 7
when (wire) {
0 -> {
var n = 0L
var shift = 0
while (i < body.size) {
val x = body[i++].toInt() and 0xFF
n = n or ((x and 0x7F).toLong() shl shift)
if (x and 0x80 == 0) break
shift += 7
}
}
2 -> {
var len = 0
var shift = 0
while (i < body.size) {
val x = body[i++].toInt() and 0xFF
len = len or ((x and 0x7F) shl shift)
if (x and 0x80 == 0) break
shift += 7
}
if (i + len > body.size) break
val emb = body.copyOfRange(i, i + len)
i += len
if (field == 1) {
val id = embId(emb)
val kind = embKind(emb)
if (id >= 0) out.add(ChanInfo(id, kind))
}
}
1 -> i += 8
5 -> i += 4
else -> break
}
}
return out
}
private fun embId(emb: ByteArray): Int {
var i = 0
while (i < emb.size) {
val b = emb[i++].toInt() and 0xFF
val field = b shr 3
val wire = b and 7
when (wire) {
0 -> {
var n = 0L
var shift = 0
while (i < emb.size) {
val x = emb[i++].toInt() and 0xFF
n = n or ((x and 0x7F).toLong() shl shift)
if (x and 0x80 == 0) break
shift += 7
}
if (field == 1) return n.toInt()
}
2 -> {
var len = 0
var shift = 0
while (i < emb.size) {
val x = emb[i++].toInt() and 0xFF
len = len or ((x and 0x7F) shl shift)
if (x and 0x80 == 0) break
shift += 7
}
i += len
}
1 -> i += 8
5 -> i += 4
else -> return -1
}
}
return -1
}
private fun embKind(emb: ByteArray): String {
var i = 0
while (i < emb.size) {
val b = emb[i++].toInt() and 0xFF
val field = b shr 3
val wire = b and 7
when (wire) {
0 -> {
while (i < emb.size) {
val x = emb[i++].toInt() and 0xFF
if (x and 0x80 == 0) break
}
}
2 -> {
var len = 0
var shift = 0
while (i < emb.size) {
val x = emb[i++].toInt() and 0xFF
len = len or ((x and 0x7F) shl shift)
if (x and 0x80 == 0) break
shift += 7
}
i += len
when (field) {
2 -> return "sensor"
3 -> return "av"
4 -> return "input"
5 -> return "avin"
6 -> return "bt"
8 -> return "nav"
12 -> return "vendor"
}
}
1 -> i += 8
5 -> i += 4
else -> return "unk"
}
}
return "unk"
}
fun run(
io: AoaBulkIo,
ssl: AoaSslSession,
log: (String) -> Unit,
holdMs: Long = 28_000L,
onVideoChannel: (Int) -> Unit = {}
): Result {
var discovery = false
var videoCh = CH_VIDEO
var rxFrames = 0
var sdSent = 0
var opened = 0
val notes = StringBuilder()
val nameVariants = listOf(
"AmniDrive" to "Amni",
"Android Auto" to "Google",
"Galaxy" to "samsung",
"Pixel" to "Google"
)
var preferPlain = true
var authFailed = false
fun txPlain(msgId: Int, body: ByteArray, tag: String) {
val frame = AaAapVersion.encodeMessage(CH_CONTROL, msgId, body)
io.write(frame)
log("POST-TLS TX plain $tag ${frame.size}B hex=${AaAapVersion.hexHead(frame)}")
}
fun txEncFrame(msgId: Int, body: ByteArray, tag: String) {
try {
val plain = ByteArray(2 + body.size)
plain[0] = ((msgId ushr 8) and 0xFF).toByte()
plain[1] = (msgId and 0xFF).toByte()
System.arraycopy(body, 0, plain, 2, body.size)
val cipher = ssl.encryptApp(plain)
val frame = AaAapVersion.encodeFrame(CH_CONTROL, AaAapVersion.frameFlags(encrypted = true), cipher)
io.write(frame)
log("POST-TLS TX enc $tag msg=0x${msgId.toString(16)} plain=${plain.size} cipher=${cipher.size}")
} catch (e: Exception) {
log("POST-TLS TX enc fail $tag ${e.message}")
}
}
fun txTunnel(msgId: Int, body: ByteArray, tag: String) {
try {
val aap = AaAapVersion.encodeMessage(CH_CONTROL, msgId, body)
val tls = ssl.encryptApp(aap)
io.write(tls)
log("POST-TLS TX tunnel $tag aap=${aap.size} tls=${tls.size}")
} catch (e: Exception) {
log("POST-TLS TX tunnel fail $tag ${e.message}")
}
}
fun txControl(msgId: Int, body: ByteArray, tag: String, forceModes: String = "auto") {
val modes = when (forceModes) {
"plain" -> listOf("plain")
"enc" -> listOf("enc", "tunnel")
"all" -> listOf("plain", "enc", "tunnel")
else -> if (preferPlain) listOf("plain", "enc") else listOf("enc", "plain", "tunnel")
}
for (m in modes) {
when (m) {
"plain" -> txPlain(msgId, body, tag)
"enc" -> txEncFrame(msgId, body, tag)
"tunnel" -> txTunnel(msgId, body, tag)
}
}
}
fun sendAuth() {
log("POST-TLS send AuthComplete.OK (plain-first, HU live uses flags=0x03)")
txControl(MSG_AUTH_COMPLETE, authCompleteBody(), "AuthComplete.OK", "all")
}
fun sendDiscovery(why: String) {
val idx = (sdSent++).coerceAtMost(nameVariants.lastIndex)
val (name, brand) = nameVariants[idx]
discovery = true
notes.append("sd-req-$why-$name;")
log("POST-TLS send ServiceDiscoveryRequest ($why / $name/$brand) plainPrefer=$preferPlain authFail=$authFailed")
txControl(MSG_SERVICE_DISCOVERY_REQUEST, serviceDiscoveryRequestBody(name, brand), "ServiceDiscoveryRequest-$name", if (authFailed) "all" else "auto")
}
fun openChannel(chId: Int, kind: String) {
if (chId <= 0) return
notes.append("open-$kind-$chId;")
log("POST-TLS send ChannelOpenRequest ch=$chId kind=$kind")
txControl(MSG_CHANNEL_OPEN_REQUEST, channelOpenRequestBody(chId, 0), "ChannelOpenRequest-$chId")
if (kind == "av" || kind == "unk") {
videoCh = chId
onVideoChannel(chId)
}
opened++
}
fun handleMsg(channel: Int, msgId: Int, body: ByteArray, via: String) {
rxFrames++
log("POST-TLS RX $via ch=$channel msg=0x${msgId.toString(16)} body=${body.size} hex=${AaAapVersion.hexHead(body, 24)}")
when (msgId) {
MSG_AUTH_COMPLETE -> {
val st = parseStatus(body)
notes.append("auth-rx-st$st;")
log("POST-TLS AuthComplete status=$st (0=OK; nonzero=HU cert/auth reject)")
if (st != 0 && st != -1) {
authFailed = true
preferPlain = true
notes.append("auth-fail;")
log("POST-TLS AUTH FAIL status=$st — HU rejected host identity (self-signed/non-Google cert wall). Still probing plain SD.")
txControl(MSG_AUTH_COMPLETE, authCompleteBody(), "AuthComplete.OK-retry", "plain")
}
if (sdSent == 0) sendDiscovery("after-auth-rx")
}
MSG_SERVICE_DISCOVERY_REQUEST -> {
notes.append("sd-req-from-hu;")
log("POST-TLS HU sent ServiceDiscoveryRequest (unexpected for phone role) — still send our Request")
if (sdSent == 0) sendDiscovery("hu-sent-req")
}
MSG_SERVICE_DISCOVERY_RESPONSE -> {
notes.append("sd-rsp;")
discovery = true
val chans = parseChannels(body)
log("POST-TLS ServiceDiscoveryResponse channels=${chans.size} ${chans.joinToString { "${it.id}:${it.kind}" }}")
if (chans.isEmpty()) {
notes.append("sd-empty;")
openChannel(CH_VIDEO, "av")
openChannel(CH_INPUT, "input")
openChannel(CH_SENSOR, "sensor")
} else {
for (c in chans) openChannel(c.id, c.kind)
}
}
MSG_CHANNEL_OPEN_REQUEST -> {
val chId = parseChannelId(body).let { if (it > 0) it else CH_VIDEO }
videoCh = chId
notes.append("ch-open-req-$chId;")
log("POST-TLS ChannelOpenRequest channel=$chId")
txControl(MSG_CHANNEL_OPEN_RESPONSE, AaPb.u32(1, STATUS_OK), "ChannelOpenResponse")
onVideoChannel(chId)
}
MSG_CHANNEL_OPEN_RESPONSE -> {
val st = parseStatus(body)
notes.append("ch-open-rsp-st$st;")
log("POST-TLS ChannelOpenResponse status=$st")
}
MSG_PING_REQUEST -> txControl(MSG_PING_RESPONSE, body, "PingResponse")
MSG_NAV_FOCUS_REQUEST -> txControl(MSG_NAV_FOCUS_RESPONSE, AaPb.u32(1, 1), "NavFocus")
MSG_AUDIO_FOCUS_REQUEST -> txControl(MSG_AUDIO_FOCUS_RESPONSE, AaPb.u32(1, 1), "AudioFocus")
MSG_SHUTDOWN_REQUEST -> {
notes.append("shutdown;")
txControl(MSG_SHUTDOWN_RESPONSE, AaPb.u32(1, STATUS_OK), "Shutdown")
}
MSG_UNKNOWN_FF, 0xff -> {
notes.append("ff;")
log("POST-TLS HU 0xff — retry SD Request variant")
if (sdSent < nameVariants.size) sendDiscovery("after-ff")
}
else -> notes.append("unk-0x${msgId.toString(16)};")
}
}
fun handleAapBuffer(buf: ByteArray, via: String): Int {
var off = 0
var consumed = 0
while (off + 4 <= buf.size) {
if (buf.size - off < 4) break
val channel = buf[off].toInt() and 0xFF
val flags = buf[off + 1].toInt() and 0xFF
val size = ((buf[off + 2].toInt() and 0xFF) shl 8) or (buf[off + 3].toInt() and 0xFF)
if (size < 0 || size > 1_000_000) break
if (buf.size - off < 4 + size) break
val rawPayload = if (size > 0) buf.copyOfRange(off + 4, off + 4 + size) else ByteArray(0)
val frameLen = 4 + size
val enc = (flags and AaAapVersion.ENC_ENCRYPTED) != 0
var msgId = -1
var body = ByteArray(0)
if (enc) {
if (rawPayload.size == 2) {
msgId = ((rawPayload[0].toInt() and 0xFF) shl 8) or (rawPayload[1].toInt() and 0xFF)
body = ByteArray(0)
log("POST-TLS $via short-enc as plain msg=0x${msgId.toString(16)}")
} else {
try {
val plain = ssl.decryptApp(rawPayload)
if (plain.size >= 2) {
msgId = ((plain[0].toInt() and 0xFF) shl 8) or (plain[1].toInt() and 0xFF)
body = if (plain.size > 2) plain.copyOfRange(2, plain.size) else ByteArray(0)
} else {
log("POST-TLS $via enc short plain=${plain.size}")
off += frameLen
consumed = off
continue
}
} catch (e: Exception) {
if (rawPayload.size >= 2) {
msgId = ((rawPayload[0].toInt() and 0xFF) shl 8) or (rawPayload[1].toInt() and 0xFF)
body = if (rawPayload.size > 2) rawPayload.copyOfRange(2, rawPayload.size) else ByteArray(0)
log("POST-TLS $via enc-fallback plain-parse msg=0x${msgId.toString(16)}")
} else {
log("POST-TLS $via decrypt fail ${e.message}")
off += frameLen
consumed = off
continue
}
}
}
} else {
if (rawPayload.size < 2) {
off += frameLen
consumed = off
continue
}
msgId = ((rawPayload[0].toInt() and 0xFF) shl 8) or (rawPayload[1].toInt() and 0xFF)
body = if (rawPayload.size > 2) rawPayload.copyOfRange(2, rawPayload.size) else ByteArray(0)
}
handleMsg(channel, msgId, body, via)
off += frameLen
consumed = off
}
return consumed
}
sendAuth()
val t0 = System.currentTimeMillis()
val aapHold = ByteArrayOutputStream()
val tlsHold = ByteArrayOutputStream()
val plainHold = ByteArrayOutputStream()
var lastSdAt = 0L
while (System.currentTimeMillis() - t0 < holdMs && io.isAlive()) {
val chunk = io.waitDrain(120)
if (chunk.isEmpty()) {
if (sdSent == 0 && System.currentTimeMillis() - t0 > 1_500L) {
sendDiscovery("timeout-proactive")
lastSdAt = System.currentTimeMillis()
} else if (sdSent in 1 until nameVariants.size && opened == 0 && System.currentTimeMillis() - lastSdAt > 4_000L) {
lastSdAt = System.currentTimeMillis()
sendDiscovery("retry-empty")
}
continue
}
log("POST-TLS bulk ${chunk.size} head=${AaAapVersion.hexHead(chunk, 14)}")
val first = chunk[0].toInt() and 0xFF
if (first == 0x17 || first == 0x16 || first == 0x15) {
tlsHold.write(chunk)
try {
val plain = ssl.decryptApp(tlsHold.toByteArray())
if (plain.isNotEmpty()) {
tlsHold.reset()
plainHold.write(plain)
log("POST-TLS tunnel plain ${plain.size} head=${AaAapVersion.hexHead(plain, 14)}")
val all = plainHold.toByteArray()
val c = handleAapBuffer(all, "tunnel")
if (c > 0) {
plainHold.reset()
if (c < all.size) plainHold.write(all, c, all.size - c)
}
}
} catch (e: Exception) {
if (tlsHold.size() > 64_000) tlsHold.reset()
log("POST-TLS tunnel wait ${e.message}")
}
} else {
aapHold.write(chunk)
val all = aapHold.toByteArray()
val c = handleAapBuffer(all, "aap")
if (c > 0) {
aapHold.reset()
if (c < all.size) aapHold.write(all, c, all.size - c)
} else if (all.size > 8_000) {
aapHold.reset()
}
try {
val maybe = ssl.decryptApp(chunk)
if (maybe.isNotEmpty()) {
plainHold.write(maybe)
val allp = plainHold.toByteArray()
val cp = handleAapBuffer(allp, "rawdec")
if (cp > 0) {
plainHold.reset()
if (cp < allp.size) plainHold.write(allp, cp, allp.size - cp)
}
}
} catch (_: Exception) {}
}
if (sdSent == 0 && System.currentTimeMillis() - t0 > 1_500L) {
sendDiscovery("timeout-proactive")
lastSdAt = System.currentTimeMillis()
}
}
return Result(discovery, videoCh, rxFrames, notes.toString())
}
}
