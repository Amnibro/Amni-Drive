package com.amni.drive.link
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
class LinkProtoTest {
@Test
fun roundTripFrame() {
val bos = ByteArrayOutputStream()
val payload = byteArrayOf(1, 2, 3, 4, 5)
LinkProto.write(bos, LinkProto.FRAME, payload)
val msg = LinkProto.read(ByteArrayInputStream(bos.toByteArray()))
assertNotNull(msg)
assertEquals(LinkProto.FRAME, msg!!.first)
assertArrayEquals(payload, msg.second)
}
@Test
fun helloAndTouch() {
val h = LinkProto.helloPayload("HOST", "Test")
val (role, name) = LinkProto.parseHello(h)
assertEquals("HOST", role)
assertEquals("Test", name)
val t = LinkProto.touchPayload(LinkProto.TOUCH_DOWN, 0.25f, 0.75f, 3)
val p = LinkProto.parseTouch(t)
assertEquals(LinkProto.TOUCH_DOWN.toFloat(), p[0], 0.01f)
assertEquals(0.25f, p[1], 0.001f)
assertEquals(0.75f, p[2], 0.001f)
assertEquals(3f, p[3], 0.01f)
}
@Test
fun videoCfg() {
val raw = LinkProto.videoCfg(1920, 720, 12, 55)
val c = LinkProto.parseVideoCfg(raw)
assertEquals(1920, c[0])
assertEquals(720, c[1])
assertEquals(12, c[2])
assertEquals(55, c[3])
}
@Test
fun rejectsBadMagic() {
val bos = ByteArrayOutputStream()
bos.write(byteArrayOf(0, 0, 0, 0, 1, 4, 0, 0, 0, 0, 0))
assertEquals(null, LinkProto.read(ByteArrayInputStream(bos.toByteArray())))
}
}
