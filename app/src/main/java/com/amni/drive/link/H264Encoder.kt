package com.amni.drive.link

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.SystemClock
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
    var onAccessUnitTyped: (bytes: ByteArray, keyFrame: Boolean, codecConfig: Boolean, ptsUs: Long) -> Unit = { _, _, _, _ -> }
    var onError: (String) -> Unit = {}

    fun start(): Boolean {
        val targetFps = fps.coerceIn(15, 30)
        val maxInFps = minOf(targetFps, 24).toFloat()
        val dropSets = listOf(
            emptySet<String>(),
            setOf(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES),
            setOf(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES, MediaFormat.KEY_MAX_FPS_TO_ENCODER, MediaFormat.KEY_MAX_B_FRAMES, MediaFormat.KEY_LATENCY, MediaFormat.KEY_PRIORITY),
            setOf(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES, MediaFormat.KEY_MAX_FPS_TO_ENCODER, MediaFormat.KEY_MAX_B_FRAMES, MediaFormat.KEY_LATENCY, MediaFormat.KEY_PRIORITY, MediaFormat.KEY_PROFILE, MediaFormat.KEY_LEVEL, MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER)
        )
        var lastErr: Exception? = null
        for (dropped in dropSets) {
            var c: MediaCodec? = null
            try {
                val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, targetFps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 3)
                    if (MediaFormat.KEY_MAX_FPS_TO_ENCODER !in dropped) {
                        setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, maxInFps)
                    }
                    if (MediaFormat.KEY_PROFILE !in dropped) {
                        setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
                    }
                    if (MediaFormat.KEY_LEVEL !in dropped) {
                        setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel4)
                    }
                    if (MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER !in dropped) {
                        setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 333_333L)
                    }
                    if (MediaFormat.KEY_PRIORITY !in dropped) {
                        setInteger(MediaFormat.KEY_PRIORITY, 0)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && MediaFormat.KEY_LATENCY !in dropped) {
                        setInteger(MediaFormat.KEY_LATENCY, 1)
                    }
                    if (MediaFormat.KEY_MAX_B_FRAMES !in dropped) {
                        setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
                    }
                    if (MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES !in dropped) {
                        setInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES, 1)
                    }
                }
                c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                val surf = c.createInputSurface()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    runCatching { surf.setFrameRate(maxInFps, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT) }
                }
                c.start()
                inputSurface = surf
                codec = c
                running.set(true)
                return true
            } catch (e: Exception) {
                lastErr = e
                runCatching { c?.release() }
            }
        }
        onError(lastErr?.message ?: "encoder start failed")
        return false
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
                        val isCfg = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        val isKey = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                        val ptsUs = SystemClock.elapsedRealtimeNanos() / 1000L
                        onAccessUnit(bytes, isKey || isCfg)
                        onAccessUnitTyped(bytes, isKey, isCfg, ptsUs)
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
