package com.amni.drive.wire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
class AaAapVersionTest {
@Test
fun aasdkFrameHeaderIsTwoBytesPlusSize() {
val raw = AaAapVersion.versionResponse(1, 6, 0)
assertEquals(0, raw[0].toInt() and 0xFF)
assertEquals(AaAapVersion.FRAME_BULK, raw[1].toInt() and 0xFF)
val size = ((raw[2].toInt() and 0xFF) shl 8) or (raw[3].toInt() and 0xFF)
assertEquals(2 + 6, size)
assertEquals(4 + size, raw.size)
}
@Test
fun versionResponseRoundTrip() {
val raw = AaAapVersion.versionResponse(1, 1, 0)
val p = AaAapVersion.tryParse(raw)
assertNotNull(p)
assertEquals(AaAapVersion.CHANNEL_CONTROL, p!!.channel)
assertEquals(AaAapVersion.MSG_VERSION_RESPONSE, p.msgId)
assertEquals(6, p.payload.size)
assertEquals(1, ((p.payload[0].toInt() and 0xFF) shl 8) or (p.payload[1].toInt() and 0xFF))
assertEquals(1, ((p.payload[2].toInt() and 0xFF) shl 8) or (p.payload[3].toInt() and 0xFF))
}
@Test
fun probeMinorsIncludeAasdkDefault() {
assertTrue(AaAapVersion.PROBE_MINORS.contains(1))
assertEquals(1, AaAapVersion.MINOR)
}
@Test
fun versionRequestParse() {
val raw = AaAapVersion.versionRequest(1, 5)
val p = AaAapVersion.tryParse(raw)
assertNotNull(p)
assertEquals(AaAapVersion.MSG_VERSION_REQUEST, p!!.msgId)
}
@Test
fun negotiateRepliesToRequest() {
val req = AaAapVersion.versionRequest()
val inp = ByteArrayInputStream(req)
val out = ByteArrayOutputStream()
val logs = mutableListOf<String>()
val ok = AaAapVersion.negotiate(inp, out, { logs.add(it) }, 900L)
assertTrue(ok)
assertTrue(out.size() > 0)
assertTrue(logs.any { it.contains("VersionResponse") })
val all = out.toByteArray()
var foundRsp = false
var off = 0
while (off < all.size) {
val p = AaAapVersion.tryParse(all, off) ?: break
if (p.msgId == AaAapVersion.MSG_VERSION_RESPONSE) foundRsp = true
off += 4 + 2 + p.payload.size
}
assertTrue(foundRsp)
}
@Test
fun phoneFirstVersionRequestEmittedWhenSilent() {
val inp = ByteArrayInputStream(ByteArray(0))
val out = ByteArrayOutputStream()
val logs = mutableListOf<String>()
AaAapVersion.negotiate(inp, out, { logs.add(it) }, 600L)
assertTrue(logs.any { it.contains("VersionRequest (phone-first)") })
assertTrue(out.size() > 0)
}
@Test
fun sslHandshakeFrameMsgId() {
val body = byteArrayOf(0x16, 0x03, 0x01, 0x00, 0x01, 0x00)
val frame = AaAapVersion.sslHandshakeFrame(body)
val p = AaAapVersion.tryParse(frame)
assertNotNull(p)
assertEquals(AaAapVersion.MSG_SSL_HANDSHAKE, p!!.msgId)
assertTrue(body.contentEquals(p.payload))
}
}
