package com.amni.drive.wire

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * Captures phone media & navigation guidance audio via Android 10+ AudioPlaybackCapture
 * and streams 20 ms PCM chunks to an active AAP MEDIA_AUDIO sink channel (USB or Wireless AA).
 *
 * When not enabled, AAP still completes ChannelOpen + MEDIA_SETUP so strict head units stay happy
 * while audio continues over Bluetooth A2DP with zero interference.
 */
object AaAudioStreamer {
    private const val TAG = "Amni.AaAudio"
    private const val SILENCE_THRESHOLD = 96
    private const val SILENCE_STOP_MS = 3_200L

    data class Sink(
        val sampleRate: Int = 48_000,
        val channels: Int = 2,
        val bits: Int = 16,
        val onStart: () -> Unit,
        val onPcm: (pcm: ByteArray, ptsUs: Long) -> Unit,
        val onStop: () -> Unit
    )

    private val _armed = MutableStateFlow(false)
    val armed: StateFlow<Boolean> = _armed

    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming

    private val projectionRef = AtomicReference<MediaProjection?>(null)
    private val sinkRef = AtomicReference<Sink?>(null)
    private val workerRunning = AtomicBoolean(false)
    private var workerThread: Thread? = null

    fun setProjection(proj: MediaProjection?) {
        val old = projectionRef.getAndSet(proj)
        if (old != null && old !== proj) runCatching { old.stop() }
        _armed.value = proj != null
        if (proj != null && sinkRef.get() != null) {
            ensureWorker()
        } else if (proj == null) {
            stopWorker()
        }
    }

    fun attachSink(sink: Sink) {
        sinkRef.set(sink)
        if (projectionRef.get() != null) ensureWorker()
    }

    fun detachSink(sink: Sink) {
        if (sinkRef.compareAndSet(sink, null)) {
            stopWorker()
        }
    }

    fun stopAll(ctx: Context? = null) {
        stopWorker()
        projectionRef.getAndSet(null)?.let { runCatching { it.stop() } }
        _armed.value = false
        ctx?.let { runCatching { it.stopService(Intent(it, AudioCaptureService::class.java)) } }
    }

    private fun ensureWorker() {
        if (!workerRunning.compareAndSet(false, true)) return
        workerThread = Thread({ runCaptureLoop() }, "aa-pcm-audio").also {
            it.isDaemon = true
            it.start()
        }
    }

    private fun stopWorker() {
        workerRunning.set(false)
        workerThread?.interrupt()
        workerThread = null
        _streaming.value = false
    }

    @SuppressLint("MissingPermission")
    private fun runCaptureLoop() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            workerRunning.set(false)
            return
        }
        val proj = projectionRef.get()
        val sink = sinkRef.get()
        if (proj == null || sink == null) {
            workerRunning.set(false)
            return
        }
        val sampleRate = sink.sampleRate.coerceIn(16_000, 48_000)
        val chCount = if (sink.channels == 1) 1 else 2
        val chMask = if (chCount == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
        val chunkBytes = (sampleRate / 50) * chCount * 2 // 20 ms of 16-bit PCM (3840 B @ 48kHz stereo)
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, chMask, AudioFormat.ENCODING_PCM_16BIT)
            .coerceAtLeast(chunkBytes * 4)

        var record: AudioRecord? = null
        var started = false
        try {
            val cfg = AudioPlaybackCaptureConfiguration.Builder(proj)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val fmt = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(chMask)
                .build()
            record = AudioRecord.Builder()
                .setAudioFormat(fmt)
                .setBufferSizeInBytes(minBuf)
                .setAudioPlaybackCaptureConfig(cfg)
                .build()
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "AudioRecord init failed state=${record.state}")
                return
            }
            record.startRecording()
            val buf = ByteArray(chunkBytes)
            var lastSoundMs = 0L
            Log.i(TAG, "AAP audio capture loop active ${sampleRate}Hz ch=$chCount chunk=${chunkBytes}B")
            while (workerRunning.get() && sinkRef.get() === sink && projectionRef.get() === proj) {
                val n = record.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
                if (n <= 0) {
                    if (n < 0) break
                    continue
                }
                val nowMs = SystemClock.uptimeMillis()
                val audible = hasAudibleSignal(buf, n)
                if (audible) {
                    lastSoundMs = nowMs
                    if (!started) {
                        started = true
                        _streaming.value = true
                        runCatching { sink.onStart() }
                    }
                }
                if (started) {
                    val pcm = if (n == buf.size) buf.copyOf() else buf.copyOfRange(0, n)
                    val ptsUs = SystemClock.elapsedRealtimeNanos() / 1000L
                    runCatching { sink.onPcm(pcm, ptsUs) }
                    if (!audible && nowMs - lastSoundMs > SILENCE_STOP_MS) {
                        started = false
                        _streaming.value = false
                        runCatching { sink.onStop() }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AAP audio capture ended: ${e.message}")
        } finally {
            if (started) {
                runCatching { sink.onStop() }
            }
            _streaming.value = false
            runCatching { record?.stop() }
            runCatching { record?.release() }
            workerRunning.set(false)
        }
    }

    internal fun hasAudibleSignal(pcm16Le: ByteArray, len: Int = pcm16Le.size): Boolean {
        var i = 0
        val limit = len - 1
        while (i < limit) {
            val s = ((pcm16Le[i + 1].toInt() shl 8) or (pcm16Le[i].toInt() and 0xFF)).toShort().toInt()
            if (abs(s) >= SILENCE_THRESHOLD) return true
            i += 8 // sample every 4th 16-bit value for low CPU overhead
        }
        return false
    }
}

/**
 * Foreground service that holds Android's mediaProjection token required for
 * AudioPlaybackCapture on Android 10–16.
 */
class AudioCaptureService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            AaAudioStreamer.setProjection(null)
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannel()
        val notif = buildNotif()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        val code = intent?.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }
        if (code == Activity.RESULT_OK && data != null) {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val proj = runCatching { mpm.getMediaProjection(code, data) }.getOrNull()
            if (proj != null) {
                proj.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        AaAudioStreamer.setProjection(null)
                        stopSelf()
                    }
                }, null)
                AaAudioStreamer.setProjection(proj)
            } else {
                stopSelf()
            }
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        AaAudioStreamer.setProjection(null)
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Car Speaker Audio (AAP)", NotificationManager.IMPORTANCE_LOW))
    }

    private fun buildNotif(): Notification {
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL)
        else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle("Amni-Drive Car Audio")
            .setContentText("Streaming media & navigation audio to car speakers")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL = "amni_aap_audio"
        const val NOTIF_ID = 7739
        const val ACTION_START = "com.amni.drive.wire.AUDIO_CAPTURE_START"
        const val ACTION_STOP = "com.amni.drive.wire.AUDIO_CAPTURE_STOP"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
    }
}

/**
 * Transparent helper activity that asks Android for RECORD_AUDIO + MediaProjection
 * consent once so [AaAudioStreamer] can stream PCM audio directly over USB/Wireless AA.
 */
class AudioCaptureGrantActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            finish()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
        } else {
            requestProjection()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            requestProjection()
        } else {
            finish()
        }
    }

    private fun requestProjection() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJ)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PROJ && resultCode == RESULT_OK && data != null) {
            val svc = Intent(this, AudioCaptureService::class.java).apply {
                action = AudioCaptureService.ACTION_START
                putExtra(AudioCaptureService.EXTRA_CODE, resultCode)
                putExtra(AudioCaptureService.EXTRA_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
            else startService(svc)
        }
        finish()
    }

    companion object {
        private const val REQ_MIC = 4101
        private const val REQ_PROJ = 4102
        fun launch(ctx: Context) {
            val i = Intent(ctx, AudioCaptureGrantActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        }
    }
}
