package com.amni.drive.wire
import java.io.ByteArrayOutputStream
object AaPb {
fun varint(v: Long): ByteArray {
var n = v
val out = ByteArrayOutputStream(10)
while (true) {
if ((n and 0x7FL.inv()) == 0L) {
out.write(n.toInt() and 0x7F)
break
}
out.write(((n and 0x7F) or 0x80).toInt())
n = n ushr 7
}
return out.toByteArray()
}
fun key(field: Int, wire: Int): ByteArray = varint(((field.toLong() shl 3) or wire.toLong()))
fun u32(field: Int, v: Int): ByteArray = key(field, 0) + varint(v.toLong() and 0xFFFFFFFFL)
fun bool(field: Int, v: Boolean): ByteArray = u32(field, if (v) 1 else 0)
fun bytes(field: Int, data: ByteArray): ByteArray {
val k = key(field, 2)
val len = varint(data.size.toLong())
return k + len + data
}
fun str(field: Int, s: String): ByteArray = bytes(field, s.toByteArray(Charsets.UTF_8))
fun emb(field: Int, data: ByteArray): ByteArray = bytes(field, data)
fun cat(vararg parts: ByteArray): ByteArray {
val out = ByteArrayOutputStream()
for (p in parts) out.write(p)
return out.toByteArray()
}
}
