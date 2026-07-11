package com.amni.drive.wire
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
data class AaFrame(val channel: Int, val flags: Int, val payload: ByteArray) {
override fun equals(other: Any?): Boolean {
if (this === other) return true
if (other !is AaFrame) return false
return channel == other.channel && flags == other.flags && payload.contentEquals(other.payload)
}
override fun hashCode(): Int = 31 * (31 * channel + flags) + payload.contentHashCode()
}
object AaFrameCodec {
const val MAGIC = 0x41415752
const val VERSION = 1
fun encode(frame: AaFrame): ByteArray {
val bos = ByteArrayOutputStream(16 + frame.payload.size)
val dos = DataOutputStream(bos)
dos.writeInt(MAGIC)
dos.writeByte(VERSION)
dos.writeByte(frame.channel and 0xFF)
dos.writeByte(frame.flags and 0xFF)
dos.writeByte(0)
dos.writeInt(frame.payload.size)
dos.write(frame.payload)
return bos.toByteArray()
}
fun write(out: OutputStream, frame: AaFrame) {
out.write(encode(frame))
out.flush()
}
fun read(inp: InputStream): AaFrame? {
val dis = DataInputStream(inp)
val magic = try { dis.readInt() } catch (_: Exception) { return null }
if (magic != MAGIC) return null
val ver = dis.readUnsignedByte()
if (ver != VERSION) return null
val channel = dis.readUnsignedByte()
val flags = dis.readUnsignedByte()
dis.readByte()
val len = dis.readInt()
if (len < 0 || len > 16_000_000) return null
val payload = ByteArray(len)
if (len > 0) dis.readFully(payload)
return AaFrame(channel, flags, payload)
}
fun decode(bytes: ByteArray): AaFrame? = read(ByteArrayInputStream(bytes))
fun control(type: Byte, body: ByteArray = ByteArray(0)): AaFrame {
val bos = ByteArrayOutputStream(1 + body.size)
bos.write(type.toInt())
bos.write(body)
return AaFrame(AaChannels.CONTROL, AaFrameFlags.CONTROL, bos.toByteArray())
}
fun video(payload: ByteArray, keyframe: Boolean): AaFrame {
val flags = AaFrameFlags.FIRST or AaFrameFlags.LAST or if (keyframe) 0x10 else 0
return AaFrame(AaChannels.VIDEO_SINK, flags, payload)
}
fun inputTouch(action: Int, x: Float, y: Float, pointerId: Int = 0): AaFrame {
val bos = ByteArrayOutputStream(16)
val dos = DataOutputStream(bos)
dos.writeInt(action)
dos.writeFloat(x)
dos.writeFloat(y)
dos.writeInt(pointerId)
return AaFrame(AaChannels.INPUT_SOURCE, 0, bos.toByteArray())
}
fun parseControlType(payload: ByteArray): Int = if (payload.isEmpty()) -1 else payload[0].toInt() and 0xFF
fun parseTouch(payload: ByteArray): FloatArray {
val dis = DataInputStream(ByteArrayInputStream(payload))
return floatArrayOf(dis.readInt().toFloat(), dis.readFloat(), dis.readFloat(), dis.readInt().toFloat())
}
}
object AaControlType {
const val HELLO: Byte = 1
const val HELLO_ACK: Byte = 2
const val PING: Byte = 3
const val PONG: Byte = 4
const val BYE: Byte = 5
const val ERROR: Byte = 6
const val KEYFRAME_REQ: Byte = 7
const val VIDEO_CFG: Byte = 8
const val CAPABILITIES: Byte = 9
}
