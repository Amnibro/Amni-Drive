package com.amni.drive.wire
import java.io.InputStream
import java.io.OutputStream
interface AaTransport {
val name: String
fun open(): Boolean
fun close()
fun input(): InputStream?
fun output(): OutputStream?
fun isOpen(): Boolean
}
class StreamTransport(
private val label: String,
private val inp: InputStream,
private val out: OutputStream
) : AaTransport {
override val name: String = label
private var open = true
override fun open(): Boolean = open
override fun close() { open = false; runCatching { inp.close() }; runCatching { out.close() } }
override fun input(): InputStream? = if (open) inp else null
override fun output(): OutputStream? = if (open) out else null
override fun isOpen(): Boolean = open
}
class AaWireHost {
var status: (String) -> Unit = {}
var onInputTouch: (Int, Float, Float, Int) -> Unit = { _, _, _, _ -> }
var onKeyframeRequest: () -> Unit = {}
private var session = AaSession()
private var transport: AaTransport? = null
private var reader: Thread? = null
@Volatile private var running = false
fun attach(t: AaTransport) {
stop()
transport = t
session = AaSession()
if (!t.open()) {
status("WIRE · transport open failed")
return
}
session.onTransportUp()
running = true
val hello = session.buildHello()
session.markHelloSent()
write(hello)
status("WIRE · hello sent · waiting peer (${t.name})")
reader = Thread({ readLoop() }, "amni-wire-read").also { it.isDaemon = true; it.start() }
}
fun sendVideo(nalOrJpeg: ByteArray, keyframe: Boolean) {
if (!session.canStream()) return
write(AaFrameCodec.video(nalOrJpeg, keyframe))
session.markStreaming()
}
fun stop() {
running = false
reader?.interrupt()
reader = null
runCatching { write(AaFrameCodec.control(AaControlType.BYE)) }
transport?.close()
transport = null
session.close()
status("WIRE · stopped")
}
fun state(): AaSessionState = session.state
fun lastError(): String? = session.lastError
private fun readLoop() {
val inp = transport?.input() ?: return
while (running && transport?.isOpen() == true) {
val frame = try { AaFrameCodec.read(inp) } catch (_: Exception) { null } ?: break
handle(frame)
}
status("WIRE · read end · state=${session.state}")
}
private fun handle(frame: AaFrame) {
when (frame.channel) {
AaChannels.CONTROL -> {
val type = AaFrameCodec.parseControlType(frame.payload)
if (type == (AaControlType.KEYFRAME_REQ.toInt() and 0xFF)) onKeyframeRequest()
val replies = session.onFrame(frame)
replies.forEach { write(it) }
if (session.state == AaSessionState.HELLO_OK) status("WIRE · session OK · peer v${session.peerVersion}")
if (session.state == AaSessionState.ERROR) status("WIRE · error ${session.lastError}")
}
AaChannels.INPUT_SOURCE -> {
val t = runCatching { AaFrameCodec.parseTouch(frame.payload) }.getOrNull() ?: return
onInputTouch(t[0].toInt(), t[1], t[2], t[3].toInt())
}
else -> session.onFrame(frame)
}
}
private fun write(frame: AaFrame) {
val out = transport?.output() ?: return
try { AaFrameCodec.write(out, frame) } catch (_: Exception) { running = false }
}
}
