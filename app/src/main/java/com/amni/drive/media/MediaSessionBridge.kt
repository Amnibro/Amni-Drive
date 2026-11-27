package com.amni.drive.media
import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
data class IncomingMsg(val pkg: String, val appLabel: String, val sender: String, val text: String, val at: Long)
object MessageFeed {
private val _latest = MutableStateFlow<IncomingMsg?>(null)
val latest: StateFlow<IncomingMsg?> = _latest.asStateFlow()
private var lastKey = ""
fun post(m: IncomingMsg, key: String) { if (key == lastKey) return; lastKey = key; _latest.value = m }
fun dismiss() { _latest.value = null }
}
class AmniNotifListener : NotificationListenerService() {
override fun onNotificationPosted(sbn: StatusBarNotification) {
runCatching {
if (sbn.packageName == packageName) return
val n = sbn.notification ?: return
val known = MSG_PKGS.any { sbn.packageName.contains(it, true) }
if (n.category != Notification.CATEGORY_MESSAGE && !known) return
if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
val sender = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() } ?: return
val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName.substringAfterLast('.'))
MessageFeed.post(IncomingMsg(sbn.packageName, label, sender, text, sbn.postTime), "${sbn.key}|${sbn.postTime}")
}
}
companion object { private val MSG_PKGS = listOf("messaging", ".mms", "whatsapp", "telegram", "signal", "messenger", "amni.chat") }
}
data class NowPlaying(val pkg: String, val appLabel: String, val title: String, val artist: String, val art: Bitmap?, val playing: Boolean)
object MediaSessionBridge {
private const val TAG = "Amni.MediaBridge"
private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()
private var controller: MediaController? = null
private var activeControllers: List<MediaController> = emptyList()
private var started = false
private var appRef: Context? = null
private val controllerCb = object : MediaController.Callback() {
override fun onMetadataChanged(metadata: MediaMetadata?) { publish(appRef) }
override fun onPlaybackStateChanged(state: PlaybackState?) { publish(appRef) }
override fun onSessionDestroyed() { controller = null; _nowPlaying.value = null; appRef?.let { refreshSessions(it) } }
}
fun hasAccess(ctx: Context): Boolean = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
fun requestAccess(ctx: Context) {
runCatching { ctx.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
fun ensureStarted(ctx: Context) {
if (!started) start(ctx) else refreshSessions(ctx.applicationContext)
}
fun start(ctx: Context) {
if (!hasAccess(ctx)) return
val app = ctx.applicationContext
appRef = app
if (started) { refreshSessions(app); return }
val msm = app.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
val comp = ComponentName(app, AmniNotifListener::class.java)
runCatching {
msm.addOnActiveSessionsChangedListener({ list -> attach(app, list) }, comp)
attach(app, msm.getActiveSessions(comp))
started = true
Log.i(TAG, "bridge started")
}.onFailure { Log.w(TAG, "start failed ${it.message}") }
}
private fun refreshSessions(app: Context) {
if (!hasAccess(app)) return
val msm = app.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
val comp = ComponentName(app, AmniNotifListener::class.java)
runCatching { attach(app, msm.getActiveSessions(comp)) }
}
private fun attach(ctx: Context, list: List<MediaController>?) {
activeControllers = list.orEmpty()
val best = activeControllers.sortedByDescending { c ->
val st = c.playbackState?.state
(if (st == PlaybackState.STATE_PLAYING) 2 else if (st == PlaybackState.STATE_PAUSED) 1 else 0)
}.firstOrNull()
controller?.unregisterCallback(controllerCb)
controller = best
best?.registerCallback(controllerCb)
publish(ctx)
}
private fun findController(pkg: String?): MediaController? =
if (pkg.isNullOrBlank()) controller else activeControllers.firstOrNull { it.packageName.equals(pkg, true) } ?: controller?.takeIf { it.packageName.equals(pkg, true) }
private fun publish(ctx: Context? = null) {
val c = controller ?: run { _nowPlaying.value = null; return }
val md = c.metadata
val label = ctx?.let { runCatching { it.packageManager.getApplicationLabel(it.packageManager.getApplicationInfo(c.packageName, 0)).toString() }.getOrNull() } ?: _nowPlaying.value?.appLabel ?: c.packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
_nowPlaying.value = NowPlaying(
c.packageName,
label,
md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
c.playbackState?.state == PlaybackState.STATE_PLAYING
)
}
fun playPause(): Boolean = controller?.let { val playing = it.playbackState?.state == PlaybackState.STATE_PLAYING; if (playing) it.transportControls.pause() else it.transportControls.play(); true } ?: false
fun playPausePackage(pkg: String): Boolean = findController(pkg)?.let { val playing = it.playbackState?.state == PlaybackState.STATE_PLAYING; if (playing) it.transportControls.pause() else it.transportControls.play(); true } ?: false
fun playPackage(pkg: String): Boolean = findController(pkg)?.let { it.transportControls.play(); true } ?: false
fun playSearch(pkg: String, query: String): Boolean = findController(pkg)?.let { it.transportControls.playFromSearch(query, android.os.Bundle()); true } ?: false
fun next(pkg: String? = null): Boolean = findController(pkg)?.let { it.transportControls.skipToNext(); true } ?: false
fun prev(pkg: String? = null): Boolean = findController(pkg)?.let { it.transportControls.skipToPrevious(); true } ?: false
fun play(): Boolean = controller?.let { it.transportControls.play(); true } ?: false
}
