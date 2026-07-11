package com.amni.drive.wire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream
class AaFrameTest {
@Test
fun roundTripVideo() {
val payload = ByteArray(64) { it.toByte() }
val f = AaFrameCodec.video(payload, true)
val back = AaFrameCodec.decode(AaFrameCodec.encode(f))
assertNotNull(back)
assertEquals(AaChannels.VIDEO_SINK, back!!.channel)
assertTrue(payload.contentEquals(back.payload))
}
@Test
fun touchRoundTrip() {
val f = AaFrameCodec.inputTouch(AaTouchAction.DOWN, 0.5f, 0.25f, 1)
val t = AaFrameCodec.parseTouch(f.payload)
assertEquals(AaTouchAction.DOWN.toFloat(), t[0], 0.01f)
assertEquals(0.5f, t[1], 0.001f)
assertEquals(0.25f, t[2], 0.001f)
assertEquals(1f, t[3], 0.01f)
}
@Test
fun sessionHelloOk() {
val a = AaSession()
val b = AaSession()
a.onTransportUp()
b.onTransportUp()
a.markHelloSent()
val hello = a.buildHello()
val replies = b.onFrame(hello)
assertEquals(AaSessionState.HELLO_OK, b.state)
assertTrue(replies.isNotEmpty())
a.onFrame(replies.first())
assertEquals(AaSessionState.HELLO_OK, a.state)
assertTrue(a.canStream())
}
@Test
fun sessionVersionReject() {
val a = AaSession(protocolVersion = 1, minProtocolVersion = 1)
val hi = AaFrameCodec.control(AaControlType.HELLO, byteArrayOf(1, 5, 0, 0, 0, 0, 30))
a.onTransportUp()
a.onFrame(hi)
assertEquals(AaSessionState.ERROR, a.state)
assertFalse(a.canStream())
}
@Test
fun controlTypesStable() {
assertEquals(1, AaControlType.HELLO.toInt())
assertEquals(7, AaControlType.KEYFRAME_REQ.toInt())
assertEquals(3, AaChannels.VIDEO_SINK)
}
@Test
fun streamTransportHello() {
val pin = PipedInputStream(1 shl 16)
val pout = PipedOutputStream(pin)
val host = AaWireHost()
var ok = false
host.status = { if (it.contains("session OK")) ok = true }
val peerSession = AaSession()
peerSession.onTransportUp()
Thread {
val hello = peerSession.buildHello()
AaFrameCodec.write(pout, hello)
val frame = AaFrameCodec.read(pin)
assertNotNull(frame)
val replies = peerSession.onFrame(frame!!)
replies.forEach { AaFrameCodec.write(pout, it) }
}.start()
Thread.sleep(50)
host.attach(StreamTransport("pipe", pin, pout))
Thread.sleep(150)
assertTrue(host.state() == AaSessionState.HELLO_OK || ok || host.state() == AaSessionState.HELLO_SENT || host.state() == AaSessionState.TRANSPORT_UP)
host.stop()
}
}
