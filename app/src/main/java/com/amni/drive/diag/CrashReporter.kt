package com.amni.drive.diag
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.amni.drive.BuildConfig
import com.amni.drive.launcher.CarApps
import com.amni.drive.media.MediaSessionBridge
import com.amni.drive.ui.CrashReportActivity
import com.amni.drive.wire.AaAudioStreamer
import com.amni.drive.wire.AaCertStore
import com.amni.drive.wire.AaPostTls
import com.amni.drive.wire.WirelessAaService
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.concurrent.TimeUnit
object CrashReporter {
    const val SUPPORT_EMAIL = LogScrubber.SUPPORT_EMAIL
    private const val GATEWAY_URL = "https://amni-map.amni-map-gateway.workers.dev/feedback"
    private const val PREFS = "amni_diag"
    private const val KEY_UNSENT = "unsent_crash_path"
    private const val KEY_UPLOADED = "uploaded_crash_path"
    private const val CHANNEL_ID = "amni_crash_alerts"
    private const val NOTIF_ID = 7747
    private val ring = ArrayDeque<String>(270)
    @Volatile private var installed = false
    fun breadcrumb(tag: String, msg: String) {
        val line = "${Instant.now()} [$tag] ${LogScrubber.scrub(msg)}"
        synchronized(ring) {
            if (ring.size >= 270) ring.removeFirst()
            ring.addLast(line)
        }
    }
    fun install(appCtx: Context) {
        if (installed) return
        installed = true
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val file = captureReport(appCtx, "CRASH", "", throwable, thread.name)
                val summary = buildGatewaySummary("CRASH", throwable, file)
                val uploader = Thread({
                    if (uploadGatewaySummary(summary)) markUploaded(appCtx, file)
                }, "amni-crash-upload")
                uploader.start()
                runCatching { notifyAndOpenCrashUi(appCtx, file) }
                runCatching { uploader.join(1700L) }
            }
            prev?.uncaughtException(thread, throwable) ?: run {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }
    fun latestUnsent(ctx: Context): File? {
        val path = ctx.getSharedPreferences(PREFS, 0).getString(KEY_UNSENT, null) ?: return null
        return File(path).takeIf { it.exists() && it.length() > 0L }
    }
    fun dismissUnsent(ctx: Context) {
        ctx.getSharedPreferences(PREFS, 0).edit().remove(KEY_UNSENT).apply()
    }
    fun isUploaded(ctx: Context, file: File): Boolean =
        ctx.getSharedPreferences(PREFS, 0).getString(KEY_UPLOADED, null) == file.absolutePath
    fun markUploaded(ctx: Context, file: File) {
        ctx.getSharedPreferences(PREFS, 0).edit().putString(KEY_UPLOADED, file.absolutePath).commit()
    }
    fun captureReport(ctx: Context, kind: String, userNote: String = "", throwable: Throwable? = null, threadName: String? = null): File {
        val dir = File(ctx.filesDir, "crash_reports").also { it.mkdirs() }
        val ts = System.currentTimeMillis()
        val out = File(dir, "amni-${kind.lowercase()}-$ts.txt")
        val dm = ctx.resources.displayMetrics
        val cert = runCatching { AaCertStore.status(ctx) }.getOrNull()
        val perf = runCatching { AaPostTls.perfStats.value }.getOrNull()
        val waa = runCatching { WirelessAaService.state.value }.getOrNull()
        val header = buildString {
            appendLine("=== AMNI-DRIVE DIAGNOSTIC REPORT (PRIVACY SCRUBBED) ===")
            appendLine("Kind: $kind")
            appendLine("Time: ${Instant.ofEpochMilli(ts)}")
            appendLine("Recipient: $SUPPORT_EMAIL")
            appendLine("App: Amni-Drive v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT}) · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("PhoneDisplay: ${dm.widthPixels}x${dm.heightPixels} @ ${dm.densityDpi}dpi")
            appendLine("State: cert=${cert?.source ?: "n/a"}(root=${cert?.googleRootVerified}) waa=${waa?.phase ?: "idle"} audioArmed=${runCatching { AaAudioStreamer.armed.value }.getOrDefault(false)} audioLive=${runCatching { AaAudioStreamer.streaming.value }.getOrDefault(false)} shellAngel=${runCatching { CarApps.ready.value }.getOrDefault(false)} notifMedia=${runCatching { MediaSessionBridge.hasAccess(ctx) }.getOrDefault(false)}")
            if (perf != null) appendLine("Perf: fps=${perf.videoFps} drops=${perf.videoDrops} unacked=${perf.videoUnacked} avgBytes=${perf.avgFrameBytes} touches=${perf.touches}")
            if (threadName != null) appendLine("Thread: $threadName")
        }
        val crumbs = synchronized(ring) { ring.joinToString("\n") }
        val stack = throwable?.stackTraceToString().orEmpty()
        val logcat = dumpLogcat(1700L)
        val scrubbedBody = buildString {
            append(LogScrubber.scrub(header))
            if (userNote.isNotBlank()) {
                appendLine("\n=== USER DESCRIPTION ===")
                appendLine(LogScrubber.scrub(userNote.trim()))
            }
            if (stack.isNotBlank()) {
                appendLine("\n=== UNCAUGHT EXCEPTION ===")
                appendLine(LogScrubber.scrub(stack))
            }
            if (crumbs.isNotBlank()) {
                appendLine("\n=== SESSION BREADCRUMBS ===")
                appendLine(crumbs)
            }
            appendLine("\n=== SESSION LOGCAT (SCRUBBED) ===")
            appendLine(LogScrubber.scrub(logcat))
        }
        out.writeText(scrubbedBody)
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(7)?.forEach { runCatching { it.delete() } }
        if (kind == "CRASH") {
            ctx.getSharedPreferences(PREFS, 0).edit().putString(KEY_UNSENT, out.absolutePath).commit()
        }
        return out
    }
    fun buildGatewaySummary(kind: String, throwable: Throwable?, file: File, userNote: String = ""): String {
        val top = throwable?.let { "${it.javaClass.simpleName}: ${it.message.orEmpty().take(180)}\n${it.stackTrace.take(7).joinToString("\n") { el -> "at $el" }}" }
            ?: file.readText().lineSequence().take(27).joinToString("\n")
        val raw = "[AMNI-DRIVE $kind v${BuildConfig.VERSION_NAME} ${Build.MANUFACTURER} ${Build.MODEL} SDK${Build.VERSION.SDK_INT}]\n" +
            (if (userNote.isNotBlank()) "Note: ${userNote.take(350)}\n" else "") + top
        return LogScrubber.scrub(raw).take(1950)
    }
    fun uploadGatewaySummary(summary: String): Boolean = runCatching {
        val conn = (URL(GATEWAY_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 4500
            readTimeout = 4500
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        val payload = JSONObject()
            .put("version", "drv-${BuildConfig.VERSION_NAME}")
            .put("text", summary.take(1950))
            .toString()
            .toByteArray(Charsets.UTF_8)
        conn.outputStream.use { it.write(payload) }
        val code = conn.responseCode
        conn.disconnect()
        code in 200..299
    }.onFailure { Log.w("Amni.Diag", "gateway upload failed: ${it.message}") }.getOrDefault(false)
    fun emailReport(ctx: Context, file: File, isCrash: Boolean, userNote: String = "") {
        dismissUnsent(ctx)
        val subj = if (isCrash) "Amni-Drive v${BuildConfig.VERSION_NAME} Crash Log (${Build.MODEL})"
        else "Amni-Drive v${BuildConfig.VERSION_NAME} Bug Report (${Build.MODEL})"
        val preview = runCatching { file.readText() }.getOrDefault("")
        val body = buildString {
            if (userNote.isNotBlank()) {
                appendLine(userNote.trim())
                appendLine()
            }
            appendLine("Attached: privacy-scrubbed Amni-Drive session log (${file.name}).")
            appendLine("GPS coordinates, addresses, search queries, tokens, MACs, and personal identifiers were removed automatically.")
            appendLine()
            append(preview.take(12_000))
        }
        val uri = runCatching { FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file) }.getOrNull()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subj)
            putExtra(Intent.EXTRA_TEXT, body)
            if (uri != null) {
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching {
            ctx.startActivity(Intent.createChooser(send, "Send log to $SUPPORT_EMAIL").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
        if (!launched) {
            val mailto = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL")).apply {
                putExtra(Intent.EXTRA_SUBJECT, subj)
                putExtra(Intent.EXTRA_TEXT, body.take(7000))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { ctx.startActivity(mailto) }
        }
    }
    private fun notifyAndOpenCrashUi(ctx: Context, file: File) {
        val uiIntent = Intent(ctx, CrashReportActivity::class.java)
            .putExtra(CrashReportActivity.EXTRA_PATH, file.absolutePath)
            .putExtra(CrashReportActivity.EXTRA_CRASH, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm?.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Crash & Bug Reports", NotificationManager.IMPORTANCE_HIGH))
        }
        val pi = PendingIntent.getActivity(ctx, 77, uiIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Amni-Drive captured a crash log")
            .setContentText("Tap to send scrubbed report to $SUPPORT_EMAIL")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { nm?.notify(NOTIF_ID, notif) }
        runCatching { ctx.startActivity(uiIntent) }
    }
    private fun dumpLogcat(timeoutMs: Long): String = runCatching {
        val pid = android.os.Process.myPid().toString()
        val proc = ProcessBuilder("logcat", "-d", "-t", "420", "--pid=$pid", "-v", "time").redirectErrorStream(true).start()
        val out = StringBuilder()
        val reader = Thread({
            runCatching { proc.inputStream.bufferedReader().useLines { lines -> lines.forEach { out.appendLine(it) } } }
        }, "amni-logcat-read")
        reader.start()
        proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        reader.join(500L)
        runCatching { proc.destroy() }
        out.toString().ifBlank { "logcat empty" }
    }.getOrElse { "logcat unavailable: ${it.message}" }
}
