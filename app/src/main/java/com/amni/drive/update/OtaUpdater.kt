package com.amni.drive.update
import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Display
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.amni.drive.BuildConfig
import com.amni.drive.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
object OtaUpdater {
    const val TAG = "Amni.OTA"
    const val FEED_URL = "https://amni-scient.com/drive/android-latest.json"
    const val CATALOG_URL = "https://amni-scient.com/drive/apps-catalog.json"
    const val SITE_URL = "https://amni-scient.com/amni-drive"
    private const val PREFS = "amni_ota"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val KEY_AUTO_CHECK = "auto_check"
    private const val KEY_LAST_NOTIFIED = "last_notified_tag"
    private const val CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
    private const val FG_INTERVAL_MS = 45_000L
    private const val BUNDLED_META_ASSET = "bundled/amni-map.json"
    private const val BUNDLED_APK_ASSET = "bundled/amni-map.apk"
    private const val NOTIF_CHANNEL = "amni_ota_updates"
    private const val NOTIF_ID = 7409
    @Volatile private var lastFgCheckMs = 0L
    data class BundledApkInfo(
        val packageName: String,
        val versionCode: Long,
        val versionName: String,
        val size: Long,
        val sha256: String
    )
    data class RemoteRelease(
        val versionCode: Long,
        val versionName: String,
        val url: String,
        val notes: String = "",
        val sha256: String = "",
        val size: Long = 0L
    )
    data class CompanionApp(
        val id: String,
        val name: String,
        val packageName: String,
        val emoji: String,
        val blurb: String,
        val versionCode: Long,
        val versionName: String,
        val downloadUrl: String,
        val sha256: String = "",
        val size: Long = 0L,
        val carDashNative: Boolean = false,
        val requiredForCar: Boolean = false
    )
    data class CompanionAppStatus(
        val app: CompanionApp,
        val installed: Boolean,
        val installedVersionCode: Long,
        val installedVersionName: String?,
        val prebundled: Boolean = false
    ) {
        val updateAvailable: Boolean get() = installed && app.versionCode > 0L && app.versionCode > installedVersionCode
    }
    data class OtaState(
        val checking: Boolean = false,
        val autoCheckEnabled: Boolean = true,
        val currentVersionCode: Long = BuildConfig.VERSION_CODE.toLong(),
        val currentVersionName: String = BuildConfig.VERSION_NAME,
        val selfRelease: RemoteRelease? = null,
        val selfUpdateAvailable: Boolean = false,
        val bundledMap: BundledApkInfo? = null,
        val apps: List<CompanionAppStatus> = emptyList(),
        val downloadingId: String? = null,
        val downloadProgress: Float = 0f,
        val statusMsg: String? = null,
        val errorMsg: String? = null,
        val pendingInstallApkPath: String? = null,
        val lastCheckedMs: Long = 0L
    ) {
        val totalUpdatesCount: Int get() = (if (selfUpdateAvailable) 1 else 0) + apps.count { it.updateAvailable }
        val bundledMapMissing: Boolean get() = bundledMap != null && apps.firstOrNull { it.app.id == "amni-map" }?.installed == false
        val bundledMapUpdateReady: Boolean get() = bundledMap != null && apps.firstOrNull { it.app.id == "amni-map" }?.let { it.installed && it.installedVersionCode < bundledMap.versionCode } == true
        val hasActionableBanner: Boolean get() = totalUpdatesCount > 0 || bundledMapMissing || downloadingId != null || pendingInstallApkPath != null
    }
    val FALLBACK_CATALOG = listOf(
        CompanionApp(
            id = "amni-map",
            name = "Amni-Map",
            packageName = "com.amniscient.map",
            emoji = "🗺️",
            blurb = "Prebuilt & included inside Amni-Drive · 3D buildings, trees, bridges, vehicles & offline maps",
            versionCode = 38L,
            versionName = "2.14.1",
            downloadUrl = "https://amni-scient.com/downloads/amni-map.apk",
            carDashNative = true,
            requiredForCar = true
        ),
        CompanionApp(
            id = "amni-browse",
            name = "Amni-Browse",
            packageName = "com.amniscient.browse",
            emoji = "🌐",
            blurb = "1 MB privacy-first browser with pop-up & tracker shield",
            versionCode = 13L,
            versionName = "0.17.2",
            downloadUrl = "https://amni-scient.com/downloads/amni-browse.apk"
        ),
        CompanionApp(
            id = "amni-haven",
            name = "Amni-Haven",
            packageName = "com.havenapp.mobile",
            emoji = "💬",
            blurb = "Self-hosted private chat, voice/video calls & encrypted DMs",
            versionCode = 80901L,
            versionName = "8.9.1",
            downloadUrl = "https://amni-scient.com/downloads/Haven-latest.apk"
        ),
        CompanionApp(
            id = "amni-chat",
            name = "Amni-Chat",
            packageName = "com.amniscient.chat",
            emoji = "🔒",
            blurb = "End-to-end encrypted messenger & calls with offline Bluetooth mesh",
            versionCode = 288L,
            versionName = "1.1.1",
            downloadUrl = "https://amni-scient.com/downloads/amni-chat-beta.apk"
        ),
        CompanionApp(
            id = "amni-type",
            name = "Amni-Type",
            packageName = "com.amniscient.type",
            emoji = "⌨️",
            blurb = "Private on-device swipe keyboard — nothing leaves your phone",
            versionCode = 46L,
            versionName = "0.16.3",
            downloadUrl = "https://amni-scient.com/downloads/amni-type.apk"
        ),
        CompanionApp(
            id = "amni-price",
            name = "Amni-Price",
            packageName = "com.amniscient.price",
            emoji = "🏷️",
            blurb = "Scan shelf tags & receipts to compare real store prices",
            versionCode = 1L,
            versionName = "latest",
            downloadUrl = "https://amni-scient.com/downloads/amni-price.apk"
        ),
        CompanionApp(
            id = "hedgedoc",
            name = "HedgeDoc",
            packageName = "org.hedgedoc.android",
            emoji = "📝",
            blurb = "Native Android client for your HedgeDoc notes server",
            versionCode = 121L,
            versionName = "1.2.1",
            downloadUrl = "https://github.com/Amnibro/hedgedoc-android/releases/latest/download/hedgedoc-android-signed.apk"
        )
    )
    private val _state = MutableStateFlow(OtaState())
    val state: StateFlow<OtaState> = _state
    fun readBundledAmniMap(ctx: Context): BundledApkInfo? = runCatching {
        val assets = ctx.applicationContext.assets
        val hasApk = assets.list("bundled")?.contains("amni-map.apk") == true
        if (!hasApk) return@runCatching null
        val raw = assets.open(BUNDLED_META_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val j = JSONObject(raw)
        BundledApkInfo(
            packageName = j.optString("packageName", "com.amniscient.map"),
            versionCode = j.optLong("versionCode", 37L),
            versionName = j.optString("versionName", "2.14.0"),
            size = j.optLong("size", 0L),
            sha256 = j.optString("sha256", "")
        )
    }.getOrNull()
    private fun mergeBundledIntoCatalog(catalog: List<CompanionApp>, bundled: BundledApkInfo?): List<CompanionApp> {
        if (bundled == null) return catalog
        return catalog.map { app ->
            if (app.id == "amni-map" && bundled.versionCode >= app.versionCode) {
                app.copy(
                    versionCode = bundled.versionCode,
                    versionName = bundled.versionName,
                    sha256 = bundled.sha256.ifBlank { app.sha256 },
                    size = if (bundled.size > 0L) bundled.size else app.size
                )
            } else app
        }
    }
    fun isAutoCheckEnabled(ctx: Context): Boolean =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_CHECK, true)
    fun setAutoCheckEnabled(ctx: Context, enabled: Boolean) {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_CHECK, enabled).apply()
        _state.value = _state.value.copy(autoCheckEnabled = enabled)
    }
    fun refreshInstalled(ctx: Context, catalog: List<CompanionApp> = _state.value.apps.map { it.app }.ifEmpty { FALLBACK_CATALOG }) {
        val appCtx = ctx.applicationContext
        val pm = appCtx.packageManager
        val bundled = readBundledAmniMap(appCtx)
        val mergedCatalog = mergeBundledIntoCatalog(catalog, bundled)
        val selfInfo = installedVersion(pm, appCtx.packageName)
        val curCode = selfInfo?.first ?: BuildConfig.VERSION_CODE.toLong()
        val curName = selfInfo?.second ?: BuildConfig.VERSION_NAME
        val statuses = mergedCatalog.map { app ->
            val info = installedVersion(pm, app.packageName)
            val isBundledReady = app.id == "amni-map" && bundled != null && bundled.versionCode >= app.versionCode
            CompanionAppStatus(
                app = app,
                installed = info != null,
                installedVersionCode = info?.first ?: 0L,
                installedVersionName = info?.second,
                prebundled = isBundledReady
            )
        }
        val selfRel = _state.value.selfRelease
        _state.value = _state.value.copy(
            autoCheckEnabled = isAutoCheckEnabled(appCtx),
            currentVersionCode = curCode,
            currentVersionName = curName,
            selfUpdateAvailable = selfRel != null && selfRel.versionCode > curCode,
            bundledMap = bundled,
            apps = statuses
        )
    }
    suspend fun checkOnForeground(ctx: Context) {
        refreshInstalled(ctx)
        if (!isAutoCheckEnabled(ctx)) return
        val now = System.currentTimeMillis()
        if (now - lastFgCheckMs < FG_INTERVAL_MS) return
        lastFgCheckMs = now
        checkForUpdates(ctx, quiet = true)
    }
    suspend fun checkIfDue(ctx: Context) {
        val prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_AUTO_CHECK, true)
        refreshInstalled(ctx)
        if (!enabled) return
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) {
            checkOnForeground(ctx)
            return
        }
        checkForUpdates(ctx, quiet = true)
    }
    suspend fun checkForUpdates(ctx: Context, quiet: Boolean = false) = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        val bundled = readBundledAmniMap(appCtx)
        _state.value = _state.value.copy(
            checking = true,
            bundledMap = bundled,
            errorMsg = null,
            statusMsg = if (quiet) _state.value.statusMsg else "Checking amni-scient.com for OTA updates…"
        )
        val pm = appCtx.packageManager
        val selfInfo = installedVersion(pm, appCtx.packageName)
        val curCode = selfInfo?.first ?: BuildConfig.VERSION_CODE.toLong()
        val curName = selfInfo?.second ?: BuildConfig.VERSION_NAME
        var selfRel: RemoteRelease? = _state.value.selfRelease
        var catalog: List<CompanionApp> = FALLBACK_CATALOG
        var err: String? = null
        val cb = System.currentTimeMillis() / 15_000L
        runCatching {
            val raw = fetchText("$FEED_URL?cb=$cb")
            selfRel = parseReleaseJson(raw)
        }.onFailure {
            err = "Could not reach OTA feed: ${it.message}"
            Log.w(TAG, "feed check failed: ${it.message}")
        }
        runCatching {
            val rawCat = fetchText("$CATALOG_URL?cb=$cb")
            val parsed = parseCatalogJson(rawCat)
            if (parsed.isNotEmpty()) catalog = parsed
        }.onFailure {
            Log.w(TAG, "catalog fetch failed (using built-in): ${it.message}")
        }
        val mergedCatalog = mergeBundledIntoCatalog(catalog, bundled)
        val now = System.currentTimeMillis()
        lastFgCheckMs = now
        appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_LAST_CHECK, now).apply()
        val statuses = mergedCatalog.map { app ->
            val info = installedVersion(pm, app.packageName)
            val isBundledReady = app.id == "amni-map" && bundled != null && bundled.versionCode >= app.versionCode
            CompanionAppStatus(
                app = app,
                installed = info != null,
                installedVersionCode = info?.first ?: 0L,
                installedVersionName = info?.second,
                prebundled = isBundledReady
            )
        }
        val selfAvail = selfRel != null && selfRel!!.versionCode > curCode
        val appUpdates = statuses.count { it.updateAvailable }
        val summary = when {
            err != null && quiet -> null
            err != null -> null
            selfAvail -> "Amni-Drive v${selfRel!!.versionName} is ready to install OTA"
            appUpdates > 0 -> "$appUpdates Amni companion app update${if (appUpdates > 1) "s" else ""} ready"
            !quiet -> "Everything is up to date (v$curName)"
            else -> null
        }
        _state.value = _state.value.copy(
            checking = false,
            autoCheckEnabled = isAutoCheckEnabled(appCtx),
            currentVersionCode = curCode,
            currentVersionName = curName,
            selfRelease = selfRel,
            selfUpdateAvailable = selfAvail,
            bundledMap = bundled,
            apps = statuses,
            statusMsg = summary,
            errorMsg = if (quiet) null else err,
            lastCheckedMs = now
        )
        if (selfAvail && selfRel != null) {
            maybePostSystemNotification(
                appCtx,
                tag = "drive_${selfRel!!.versionCode}",
                title = "Amni-Drive v${selfRel!!.versionName} Update Ready",
                body = selfRel!!.notes.ifBlank { "Tap to install the signed OTA update in one tap." }
            )
        } else {
            val mapUp = statuses.firstOrNull { it.app.id == "amni-map" && it.updateAvailable }
            if (mapUp != null) {
                maybePostSystemNotification(
                    appCtx,
                    tag = "map_${mapUp.app.versionCode}",
                    title = "Amni-Map v${mapUp.app.versionName} Update Ready",
                    body = if (mapUp.prebundled) "Included inside Amni-Drive — tap for instant offline install." else "Tap to install the companion 3D map update."
                )
            }
        }
    }
    private fun maybePostSystemNotification(appCtx: Context, tag: String, title: String, body: String) {
        runCatching {
            val prefs = appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getString(KEY_LAST_NOTIFIED, null) == tag) return
            val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(NOTIF_CHANNEL, "Amni-Drive Updates", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            val pi = PendingIntent.getActivity(
                appCtx,
                NOTIF_ID,
                Intent(appCtx, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_OTA, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(appCtx, NOTIF_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            nm.notify(NOTIF_ID, n)
            prefs.edit().putString(KEY_LAST_NOTIFIED, tag).apply()
        }
    }
    suspend fun installBundledAmniMap(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        val bundled = readBundledAmniMap(appCtx) ?: return@withContext false
        if (_state.value.downloadingId != null) return@withContext false
        val label = "Amni-Map v${bundled.versionName} (Built-In)"
        _state.value = _state.value.copy(
            downloadingId = "amni-map",
            downloadProgress = 0.25f,
            errorMsg = null,
            statusMsg = "Preparing built-in $label…"
        )
        try {
            val otaDir = File(appCtx.cacheDir, "ota").apply { mkdirs() }
            val apkFile = File(otaDir, "amni-map.apk")
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            val total = bundled.size.coerceAtLeast(1L)
            appCtx.assets.open(BUNDLED_APK_ASSET).use { inp ->
                FileOutputStream(apkFile).use { out ->
                    val buf = ByteArray(65536)
                    while (true) {
                        val n = inp.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        copied += n
                        _state.value = _state.value.copy(downloadProgress = (copied.toFloat() / total.toFloat()).coerceIn(0.1f, 0.99f))
                    }
                    out.flush()
                }
            }
            val gotSha = digest.digest().joinToString("") { "%02x".format(it) }
            if (bundled.sha256.isNotBlank() && !gotSha.equals(bundled.sha256, ignoreCase = true)) {
                apkFile.delete()
                throw IllegalStateException("Bundled Amni-Map SHA-256 mismatch")
            }
            _state.value = _state.value.copy(
                downloadingId = null,
                downloadProgress = 1f,
                pendingInstallApkPath = apkFile.absolutePath,
                statusMsg = "$label ready — tap Install in Android prompt"
            )
            launchApkInstaller(appCtx, apkFile, label)
        } catch (e: Exception) {
            Log.w(TAG, "installBundledAmniMap failed: ${e.message}")
            _state.value = _state.value.copy(
                downloadingId = null,
                downloadProgress = 0f,
                errorMsg = "Built-in Amni-Map install failed: ${e.message}",
                statusMsg = null
            )
            false
        }
    }
    suspend fun installPrimaryAction(ctx: Context): Boolean {
        val st = _state.value
        if (st.pendingInstallApkPath != null) return resumePendingInstall(ctx)
        if (st.selfUpdateAvailable && st.selfRelease != null) {
            val r = st.selfRelease
            return downloadAndInstall(ctx, "amni-drive", "Amni-Drive v${r.versionName}", r.url, r.sha256)
        }
        val mapSt = st.apps.firstOrNull { it.app.id == "amni-map" }
        if (mapSt != null && (!mapSt.installed || mapSt.updateAvailable)) {
            return downloadAndInstall(ctx, mapSt.app.id, "${mapSt.app.name} v${mapSt.app.versionName}", mapSt.app.downloadUrl, mapSt.app.sha256)
        }
        val nextUp = st.apps.firstOrNull { it.updateAvailable } ?: return false
        return downloadAndInstall(ctx, nextUp.app.id, "${nextUp.app.name} v${nextUp.app.versionName}", nextUp.app.downloadUrl, nextUp.app.sha256)
    }
    suspend fun downloadAndInstall(
        ctx: Context,
        id: String,
        label: String,
        url: String,
        expectedSha256: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        if (id == "amni-map") {
            val bundled = readBundledAmniMap(appCtx)
            val remoteTarget = _state.value.apps.firstOrNull { it.app.id == "amni-map" }?.app?.versionCode ?: 0L
            if (bundled != null && bundled.versionCode >= remoteTarget) {
                return@withContext installBundledAmniMap(appCtx)
            }
        }
        if (_state.value.downloadingId != null) return@withContext false
        _state.value = _state.value.copy(
            downloadingId = id,
            downloadProgress = 0.01f,
            errorMsg = null,
            statusMsg = "Downloading $label…"
        )
        try {
            val otaDir = File(appCtx.cacheDir, "ota").apply { mkdirs() }
            val safeName = id.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val tmpFile = File(otaDir, "$safeName.apk.part")
            val apkFile = File(otaDir, "$safeName.apk")
            if (tmpFile.exists()) tmpFile.delete()
            val sha = downloadWithRedirects(url, tmpFile) { frac ->
                _state.value = _state.value.copy(downloadProgress = frac.coerceIn(0.01f, 0.99f))
            }
            val cleanExpected = expectedSha256.trim().lowercase()
            if (cleanExpected.isNotEmpty() && !sha.equals(cleanExpected, ignoreCase = true)) {
                tmpFile.delete()
                throw IllegalStateException("SHA-256 verification failed (expected ${cleanExpected.take(12)}…, got ${sha.take(12)}…)")
            }
            if (apkFile.exists()) apkFile.delete()
            if (!tmpFile.renameTo(apkFile)) {
                tmpFile.copyTo(apkFile, overwrite = true)
                tmpFile.delete()
            }
            _state.value = _state.value.copy(
                downloadingId = null,
                downloadProgress = 1f,
                pendingInstallApkPath = apkFile.absolutePath,
                statusMsg = "$label verified — launching installer…"
            )
            launchApkInstaller(appCtx, apkFile, label)
        } catch (e: Exception) {
            Log.w(TAG, "downloadAndInstall($id) failed: ${e.message}")
            if (id == "amni-map" && readBundledAmniMap(appCtx) != null) {
                _state.value = _state.value.copy(downloadingId = null, downloadProgress = 0f)
                return@withContext installBundledAmniMap(appCtx)
            }
            _state.value = _state.value.copy(
                downloadingId = null,
                downloadProgress = 0f,
                errorMsg = "$label download failed: ${e.message}",
                statusMsg = null
            )
            false
        }
    }
    fun resumePendingInstall(ctx: Context): Boolean {
        val path = _state.value.pendingInstallApkPath ?: return false
        val f = File(path)
        if (!f.exists()) {
            _state.value = _state.value.copy(pendingInstallApkPath = null)
            return false
        }
        return launchApkInstaller(ctx.applicationContext, f, f.nameWithoutExtension)
    }
    private fun launchApkInstaller(appCtx: Context, apkFile: File, label: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !appCtx.packageManager.canRequestPackageInstalls()) {
            _state.value = _state.value.copy(
                statusMsg = "Enable 'Allow from this source' for Amni-Drive, then return to finish installing $label"
            )
            val permIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appCtx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startOnPhoneDisplay(appCtx, permIntent)
            return false
        }
        val uri = FileProvider.getUriForFile(appCtx, "${appCtx.packageName}.fileprovider", apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val ok = startOnPhoneDisplay(appCtx, installIntent)
        if (ok) {
            _state.value = _state.value.copy(
                pendingInstallApkPath = null,
                statusMsg = "Installer opened for $label — tap Install to finish"
            )
        } else {
            _state.value = _state.value.copy(errorMsg = "Could not open Android installer for $label")
        }
        return ok
    }
    private fun startOnPhoneDisplay(appCtx: Context, intent: Intent): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val opts = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle()
            appCtx.startActivity(intent, opts)
        } else {
            appCtx.startActivity(intent)
        }
        true
    }.onFailure {
        Log.w(TAG, "startActivity failed: ${it.message}")
    }.getOrDefault(false)
    private fun installedVersion(pm: PackageManager, pkg: String): Pair<Long, String>? = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, 0)
        }
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        code to (info.versionName ?: code.toString())
    }.getOrNull()
    internal fun parseReleaseJson(raw: String): RemoteRelease? {
        val j = JSONObject(raw)
        val code = j.optLong("versionCode", 0L)
        val name = j.optString("versionName", "").trim()
        val url = j.optString("url", "").trim()
        if (code <= 0L || name.isBlank() || url.isBlank()) return null
        return RemoteRelease(
            versionCode = code,
            versionName = name,
            url = url,
            notes = j.optString("notes", ""),
            sha256 = j.optString("sha256", ""),
            size = j.optLong("size", 0L)
        )
    }
    internal fun parseCatalogJson(raw: String): List<CompanionApp> {
        val root = JSONObject(raw)
        val arr: JSONArray = root.optJSONArray("apps") ?: return emptyList()
        val out = ArrayList<CompanionApp>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id", "").trim()
            val name = o.optString("name", "").trim()
            val pkg = o.optString("packageName", "").trim()
            val dl = o.optString("downloadUrl", "").trim()
            if (id.isBlank() || name.isBlank() || pkg.isBlank() || dl.isBlank()) continue
            out += CompanionApp(
                id = id,
                name = name,
                packageName = pkg,
                emoji = o.optString("emoji", "📦"),
                blurb = o.optString("blurb", ""),
                versionCode = o.optLong("versionCode", 0L),
                versionName = o.optString("versionName", ""),
                downloadUrl = dl,
                sha256 = o.optString("sha256", ""),
                size = o.optLong("size", 0L),
                carDashNative = o.optBoolean("carDashNative", false),
                requiredForCar = o.optBoolean("requiredForCar", false)
            )
        }
        return out
    }
    internal fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun openFollowingRedirects(startUrl: String): HttpURLConnection {
        var current = startUrl
        repeat(8) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                useCaches = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "Amni-Drive/${BuildConfig.VERSION_NAME} (Android)")
                setRequestProperty("Cache-Control", "no-cache")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrBlank()) throw IllegalStateException("Redirect missing Location header")
                current = URL(URL(current), loc).toString()
            } else {
                if (code !in 200..299) {
                    conn.disconnect()
                    throw IllegalStateException("HTTP $code")
                }
                return conn
            }
        }
        throw IllegalStateException("Too many redirects")
    }
    private fun fetchText(url: String): String {
        val conn = openFollowingRedirects(url)
        return try {
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
    private fun downloadWithRedirects(url: String, dest: File, onProgress: (Float) -> Unit): String {
        val conn = openFollowingRedirects(url)
        return try {
            val total = conn.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(32_768)
            var readTotal = 0L
            conn.inputStream.use { inp ->
                FileOutputStream(dest).use { out ->
                    while (true) {
                        val n = inp.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        readTotal += n
                        if (total > 0L) onProgress(readTotal.toFloat() / total.toFloat())
                    }
                    out.flush()
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } finally {
            conn.disconnect()
        }
    }
}
