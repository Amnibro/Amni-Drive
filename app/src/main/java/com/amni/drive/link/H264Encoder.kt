package com.amni.drive.link
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
class H264Encoder(
private val width: Int = 1920,
private val height: Int = 720,
private val fps: Int = 30,
private val bitrate: Int = 4_000_000
) {
private var codec: MediaCodec? = null
private var inputSurface: Surface? = null
private val running = AtomicBoolean(false)
var onAccessUnit: (ByteArray, Boolean) -> Unit = { _, _ -> }
var onError: (String) -> Unit = {}
fun start(): Boolean {
return try {
val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
format.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
inputSurface = c.createInputSurface()
c.start()
codec = c
running.set(true)
true
} catch (e: Exception) {
onError(e.message ?: "encoder start failed")
false
}
}
fun surface(): Surface? = inputSurface
fun requestKeyFrame() {
val c = codec ?: return
try {
val params = android.os.Bundle()
params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
c.setParameters(params)
} catch (_: Exception) {}
}
fun drain(timeoutUs: Long = 10_000L) {
val c = codec ?: return
if (!running.get()) return
val info = MediaCodec.BufferInfo()
while (true) {
val outIx = try { c.dequeueOutputBuffer(info, timeoutUs) } catch (_: Exception) { break }
when {
outIx == MediaCodec.INFO_TRY_AGAIN_LATER -> break
outIx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> continue
outIx >= 0 -> {
val buf: ByteBuffer? = c.getOutputBuffer(outIx)
if (buf != null && info.size > 0) {
val bytes = ByteArray(info.size)
buf.position(info.offset)
buf.get(bytes)
val key = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0 ||
(info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
onAccessUnit(bytes, key)
}
c.releaseOutputBuffer(outIx, false)
if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
}
else -> break
}
}
}
fun stop() {
running.set(false)
try { codec?.stop() } catch (_: Exception) {}
try { codec?.release() } catch (_: Exception) {}
try { inputSurface?.release() } catch (_: Exception) {}
codec = null
inputSurface = null
}
fun encodeBitmapFallback(bmp: Bitmap, quality: Int = 55): ByteArray {
val bos = java.io.ByteArrayOutputStream()
bmp.compress(Bitmap.CompressFormat.JPEG, quality, bos)
return bos.toByteArray()
}
}
