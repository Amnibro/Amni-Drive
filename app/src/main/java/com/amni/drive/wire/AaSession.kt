package com.amni.drive.wire
enum class AaSessionState {
IDLE,
TRANSPORT_UP,
HELLO_SENT,
HELLO_OK,
STREAMING,
ERROR,
CLOSED
}
class AaSession(
val protocolVersion: Int = AaFrameCodec.VERSION,
val minProtocolVersion: Int = 1
) {
var state: AaSessionState = AaSessionState.IDLE
private set
var peerVersion: Int = -1
private set
var lastError: String? = null
private set
var videoWidth: Int = 1920
var videoHeight: Int = 720
var videoFps: Int = 30
fun onTransportUp() {
state = when (state) {
AaSessionState.IDLE, AaSessionState.CLOSED, AaSessionState.ERROR -> AaSessionState.TRANSPORT_UP
else -> state
}
}
fun buildHello(): AaFrame {
val body = byteArrayOf(
protocolVersion.toByte(),
minProtocolVersion.toByte(),
(videoWidth shr 8).toByte(), (videoWidth and 0xFF).toByte(),
(videoHeight shr 8).toByte(), (videoHeight and 0xFF).toByte(),
videoFps.toByte()
)
return AaFrameCodec.control(AaControlType.HELLO, body)
}
fun onFrame(frame: AaFrame): List<AaFrame> {
return when (frame.channel) {
AaChannels.CONTROL -> onControl(frame)
AaChannels.INPUT_SOURCE -> emptyList()
else -> emptyList()
}
}
private fun onControl(frame: AaFrame): List<AaFrame> {
val type = AaFrameCodec.parseControlType(frame.payload)
val body = if (frame.payload.size > 1) frame.payload.copyOfRange(1, frame.payload.size) else ByteArray(0)
return when (type) {
AaControlType.HELLO.toInt() and 0xFF -> {
if (body.isNotEmpty()) peerVersion = body[0].toInt() and 0xFF
val peerMin = if (body.size > 1) body[1].toInt() and 0xFF else 1
if (peerMin > protocolVersion) {
fail("peer min $peerMin > us $protocolVersion")
listOf(AaFrameCodec.control(AaControlType.ERROR, "version".toByteArray()))
} else {
state = AaSessionState.HELLO_OK
listOf(AaFrameCodec.control(AaControlType.HELLO_ACK, byteArrayOf(protocolVersion.toByte())))
}
}
AaControlType.HELLO_ACK.toInt() and 0xFF -> {
if (body.isNotEmpty()) peerVersion = body[0].toInt() and 0xFF
state = AaSessionState.HELLO_OK
emptyList()
}
AaControlType.PING.toInt() and 0xFF -> listOf(AaFrameCodec.control(AaControlType.PONG, body))
AaControlType.PONG.toInt() and 0xFF -> emptyList()
AaControlType.KEYFRAME_REQ.toInt() and 0xFF -> emptyList()
AaControlType.BYE.toInt() and 0xFF -> {
state = AaSessionState.CLOSED
emptyList()
}
AaControlType.ERROR.toInt() and 0xFF -> {
fail(String(body, Charsets.UTF_8))
emptyList()
}
else -> emptyList()
}
}
fun markStreaming() {
if (state == AaSessionState.HELLO_OK || state == AaSessionState.STREAMING) state = AaSessionState.STREAMING
}
fun markHelloSent() {
if (state == AaSessionState.TRANSPORT_UP || state == AaSessionState.IDLE) state = AaSessionState.HELLO_SENT
}
fun fail(msg: String) {
lastError = msg
state = AaSessionState.ERROR
}
fun close() {
state = AaSessionState.CLOSED
}
fun canStream(): Boolean = state == AaSessionState.HELLO_OK || state == AaSessionState.STREAMING
}
