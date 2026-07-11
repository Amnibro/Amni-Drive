package com.amni.drive.link
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
object LinkProto {
const val MAGIC = 0x414D4E49
const val VERSION = 2
const val PORT = 17321
const val VIDEO_PORT = 17322
const val SERVICE_TYPE = "_amni-link._tcp."
const val SERVICE_NAME = "AmniDrive"
const val HELLO: Byte = 1
const val HELLO_ACK: Byte = 2
const val VIDEO_CFG: Byte = 3
const val FRAME: Byte = 4
const val TOUCH: Byte = 5
const val PING: Byte = 6
const val PONG: Byte = 7
const val BYE: Byte = 8
const val TOUCH_DOWN = 0
const val TOUCH_MOVE = 1
const val TOUCH_UP = 2
const val TOUCH_CANCEL = 3
fun write(out: OutputStream, type: Byte, payload: ByteArray = ByteArray(0)) {
val dos = DataOutputStream(out)
dos.writeInt(MAGIC)
dos.writeByte(VERSION)
dos.writeByte(type.toInt())
dos.writeByte(0)
dos.writeInt(payload.size)
if (payload.isNotEmpty()) dos.write(payload)
dos.flush()
}
fun read(inp: InputStream): Pair<Byte, ByteArray>? {
val dis = DataInputStream(inp)
val magic = try { dis.readInt() } catch (_: Exception) { return null }
if (magic != MAGIC) return null
val ver = dis.readUnsignedByte()
if (ver != VERSION) return null
val type = dis.readByte()
dis.readByte()
val len = dis.readInt()
if (len < 0 || len > 8_000_000) return null
val payload = ByteArray(len)
if (len > 0) dis.readFully(payload)
return type to payload
}
fun helloPayload(role: String, name: String): ByteArray {
val bos = ByteArrayOutputStream()
val dos = DataOutputStream(bos)
dos.writeUTF(role)
dos.writeUTF(name)
return bos.toByteArray()
}
fun parseHello(p: ByteArray): Pair<String, String> {
val dis = DataInputStream(ByteArrayInputStream(p))
return dis.readUTF() to dis.readUTF()
}
fun videoCfg(w: Int, h: Int, fps: Int, quality: Int): ByteArray {
val bos = ByteArrayOutputStream()
val dos = DataOutputStream(bos)
dos.writeInt(w); dos.writeInt(h); dos.writeInt(fps); dos.writeInt(quality)
return bos.toByteArray()
}
fun parseVideoCfg(p: ByteArray): IntArray {
val dis = DataInputStream(ByteArrayInputStream(p))
return intArrayOf(dis.readInt(), dis.readInt(), dis.readInt(), dis.readInt())
}
fun touchPayload(action: Int, x: Float, y: Float, pointerId: Int = 0): ByteArray {
val bos = ByteArrayOutputStream()
val dos = DataOutputStream(bos)
dos.writeInt(action); dos.writeFloat(x); dos.writeFloat(y); dos.writeInt(pointerId)
return bos.toByteArray()
}
fun parseTouch(p: ByteArray): FloatArray {
val dis = DataInputStream(ByteArrayInputStream(p))
return floatArrayOf(dis.readInt().toFloat(), dis.readFloat(), dis.readFloat(), dis.readInt().toFloat())
}
}
