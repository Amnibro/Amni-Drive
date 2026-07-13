package com.amni.drive.media
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
class AmniNotifListener : NotificationListenerService()
data class NowPlaying(val pkg: String, val appLabel: String, val title: String, val artist: String, val art: Bitmap?, val playing: Boolean)
object MediaSessionBridge {
private const val TAG = "Amni.MediaBridge"
private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()
private var controller: MediaController? = null
private var started = false
private val controllerCb = object : MediaController.Callback() {
override fun onMetadataChanged(metadata: MediaMetadata?) { publish() }
override fun onPlaybackStateChanged(state: PlaybackState?) { publish() }
override fun onSessionDestroyed() { controller = null; _nowPlaying.value = null }
}
fun hasAccess(ctx: Context): Boolean = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
fun requestAccess(ctx: Context) {
runCatching { ctx.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
fun start(ctx: Context) {
if (started || !hasAccess(ctx)) return
val app = ctx.applicationContext
val msm = app.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
val comp = ComponentName(app, AmniNotifListener::class.java)
runCatching {
msm.addOnActiveSessionsChangedListener({ list -> attach(app, list) }, comp)
attach(app, msm.getActiveSessions(comp))
started = true
Log.i(TAG, "bridge started")
}.onFailure { Log.w(TAG, "start failed ${it.message}") }
}
private fun attach(ctx: Context, list: List<MediaController>?) {
val best = list.orEmpty().sortedByDescending { c ->
val st = c.playbackState?.state
(if (st == PlaybackState.STATE_PLAYING) 2 else if (st == PlaybackState.STATE_PAUSED) 1 else 0)
}.firstOrNull()
controller?.unregisterCallback(controllerCb)
controller = best
best?.registerCallback(controllerCb)
publish(ctx)
}
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
fun next(): Boolean = controller?.let { it.transportControls.skipToNext(); true } ?: false
fun prev(): Boolean = controller?.let { it.transportControls.skipToPrevious(); true } ?: false
fun play(): Boolean = controller?.let { it.transportControls.play(); true } ?: false
}
