package com.amni.drive.aa
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.media.MediaBrowserServiceCompat
import com.amni.drive.R
class AmniMediaService : MediaBrowserServiceCompat() {
private var session: MediaSessionCompat? = null
override fun onCreate() {
super.onCreate()
session = MediaSessionCompat(this, "AmniDriveAA").also { s ->
s.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
s.setCallback(object : MediaSessionCompat.Callback() {
override fun onPlay() { markPlaying() }
override fun onPause() { s.setPlaybackState(state(PlaybackStateCompat.STATE_PAUSED)) }
override fun onStop() {
s.isActive = true
s.setPlaybackState(state(PlaybackStateCompat.STATE_STOPPED))
}
override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) { markPlaying() }
})
s.setPlaybackState(state(PlaybackStateCompat.STATE_PAUSED))
s.setMetadata(
MediaMetadataCompat.Builder()
.putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Amni Drive")
.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Amni on Android Auto")
.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Navigation")
.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, "Amni Drive")
.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, "Tap to open Amni media")
.build()
)
s.isActive = true
sessionToken = s.sessionToken
}
Log.i(TAG, "AmniMediaService ready · session active · no phone MainActivity launch")
}
private fun markPlaying() {
val s = session ?: return
s.isActive = true
s.setPlaybackState(state(PlaybackStateCompat.STATE_PLAYING))
s.setMetadata(
MediaMetadataCompat.Builder()
.putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Amni Drive")
.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Session live on car")
.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Amni")
.build()
)
Log.i(TAG, "media play · stay on car (no phone launch)")
}
override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
Log.i(TAG, "onGetRoot from $clientPackageName")
val extras = Bundle()
extras.putBoolean(BrowserRoot.EXTRA_OFFLINE, true)
return BrowserRoot(ROOT, extras)
}
override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
Log.i(TAG, "onLoadChildren parent=$parentId")
if (parentId != ROOT) {
result.sendResult(mutableListOf())
return
}
result.sendResult(
mutableListOf(
item(ID_HOME, "Amni Drive", "Open Amni on Android Auto"),
item(ID_HINT, "Full map UI needs Navigation host", "Media keeps Amni in the AA drawer")
)
)
}
override fun onDestroy() {
session?.isActive = false
session?.release()
session = null
super.onDestroy()
}
private fun state(st: Int) = PlaybackStateCompat.Builder()
.setActions(
PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or PlaybackStateCompat.ACTION_STOP
)
.setState(st, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
.build()
private fun item(id: String, title: String, sub: String): MediaBrowserCompat.MediaItem {
val desc = MediaDescriptionCompat.Builder()
.setMediaId(id)
.setTitle(title)
.setSubtitle(sub)
.setIconUri(android.net.Uri.parse("android.resource://${packageName}/${R.drawable.ic_amni_drive}"))
.build()
return MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
}
companion object {
private const val TAG = "Amni.MediaStub"
private const val ROOT = "amni_root"
private const val ID_HOME = "amni_home"
private const val ID_HINT = "amni_hint"
fun ensureStarted(ctx: android.content.Context) {
runCatching { ctx.startService(Intent(ctx, AmniMediaService::class.java)) }
}
}
}
