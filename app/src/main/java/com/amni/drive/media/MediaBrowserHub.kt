package com.amni.drive.media
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amni.drive.launcher.AppEntry
import com.amni.drive.ui.BindDashKeyboard
import com.amni.drive.ui.CarTouchKeyboard
import com.amni.drive.ui.DashKeyboardSpec
import com.amni.drive.ui.GlassCard
import com.amni.drive.ui.LocalAmni
import com.amni.drive.voice.VoiceSearch
import com.amni.drive.wire.AaAudioStreamer
import com.amni.drive.wire.AudioCaptureGrantActivity
data class BrowseNode(val id: String, val title: String, val subtitle: String, val browsable: Boolean, val playable: Boolean)
object MediaBrowserHub {
    private const val TAG = "Amni.MediaHub"
    private val KNOWN_AUDIO_PKGS = listOf(
        "com.spotify.music",
        "com.spotify.lite",
        "com.google.android.apps.youtube.music",
        "com.apple.android.music",
        "com.amazon.mp3",
        "com.audible.application",
        "com.pandora.android",
        "au.com.shiftyjelly.pocketcasts",
        "com.soundcloud.android",
        "com.aspiro.tidal",
        "deezer.android.app",
        "org.videolan.vlc",
        "com.maxmpz.audioplayer"
    )
    fun isAudioApp(ctx: Context, pkg: String): Boolean {
        val low = pkg.lowercase()
        if (KNOWN_AUDIO_PKGS.any { it.equals(low, true) }) return true
        if (low.contains("spotify") || low.contains("pandora") || low.contains("audible") || low.contains("pocketcasts") || low.contains("soundcloud") || low.contains("music")) return true
        return findBrowserComponent(ctx, pkg) != null
    }
    fun discoverInstalledAudioPackages(ctx: Context): Set<String> {
        val pm = ctx.packageManager
        val out = LinkedHashSet<String>()
        runCatching {
            pm.queryIntentServices(Intent("android.media.browse.MediaBrowserService"), 0).forEach { ri ->
                ri.serviceInfo?.packageName?.takeIf { it != ctx.packageName }?.let { out.add(it) }
            }
        }
        runCatching {
            pm.queryBroadcastReceivers(Intent(Intent.ACTION_MEDIA_BUTTON), 0).forEach { ri ->
                ri.activityInfo?.packageName?.takeIf { it != ctx.packageName }?.let { out.add(it) }
            }
        }
        KNOWN_AUDIO_PKGS.forEach { pkg ->
            if (runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess) out.add(pkg)
        }
        return out
    }
    fun findBrowserComponent(ctx: Context, pkg: String): ComponentName? {
        val svc = runCatching {
            ctx.packageManager.queryIntentServices(Intent("android.media.browse.MediaBrowserService").setPackage(pkg), 0).firstOrNull()?.serviceInfo
        }.getOrNull()
        if (svc != null) return ComponentName(svc.packageName, svc.name)
        if (pkg.startsWith("com.spotify.music")) {
            for (cls in listOf("com.spotify.music.libs.mediabrowserservice.SpotifyMediaBrowserService", "com.spotify.mobile.android.service.media.MediaService")) {
                val cn = ComponentName(pkg, cls)
                if (runCatching { ctx.packageManager.getServiceInfo(cn, 0) }.isSuccess) return cn
            }
        }
        return null
    }
    fun wakeAndPlay(ctx: Context, pkg: String, searchQuery: String? = null) {
        val app = ctx.applicationContext
        MediaSessionBridge.ensureStarted(app)
        if (!searchQuery.isNullOrBlank()) {
            if (MediaSessionBridge.playSearch(pkg, searchQuery)) return
        } else if (MediaSessionBridge.playPackage(pkg)) {
            return
        }
        sendTargetedMediaButton(app, pkg, KeyEvent.KEYCODE_MEDIA_PLAY)
        val cn = findBrowserComponent(app, pkg)
        if (cn != null) {
            var browserRef: MediaBrowserCompat? = null
            val cb = object : MediaBrowserCompat.ConnectionCallback() {
                override fun onConnected() {
                    val b = browserRef ?: return
                    runCatching {
                        val mc = MediaControllerCompat(app, b.sessionToken)
                        if (!searchQuery.isNullOrBlank()) mc.transportControls.playFromSearch(searchQuery, Bundle())
                        else mc.transportControls.play()
                    }.onFailure {
                        dispatchGlobalPlay(app)
                    }
                    Handler(Looper.getMainLooper()).postDelayed({ runCatching { b.disconnect() } }, 9500L)
                }
                override fun onConnectionFailed() {
                    sendTargetedMediaButton(app, pkg, KeyEvent.KEYCODE_MEDIA_PLAY)
                    dispatchGlobalPlay(app)
                }
            }
            val hints = Bundle().apply { putBoolean("android.service.media.extra.RECENT", true) }
            browserRef = MediaBrowserCompat(app, cn, cb, hints)
            runCatching { browserRef.connect() }.onFailure { dispatchGlobalPlay(app) }
        } else {
            Handler(Looper.getMainLooper()).postDelayed({
                if (!MediaSessionBridge.playPackage(pkg)) dispatchGlobalPlay(app)
            }, 450L)
        }
    }
    fun sendTargetedMediaButton(ctx: Context, pkg: String, keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        val receivers = runCatching {
            ctx.packageManager.queryBroadcastReceivers(Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg), 0)
        }.getOrDefault(emptyList())
        for (act in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val ev = KeyEvent(now, now, act, keyCode, 0)
            if (receivers.isNotEmpty()) {
                receivers.forEach { ri ->
                    val i = Intent(Intent.ACTION_MEDIA_BUTTON)
                        .setComponent(ComponentName(ri.activityInfo.packageName, ri.activityInfo.name))
                        .putExtra(Intent.EXTRA_KEY_EVENT, ev)
                        .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES or Intent.FLAG_RECEIVER_FOREGROUND)
                    runCatching { ctx.sendBroadcast(i) }
                }
            } else {
                val i = Intent(Intent.ACTION_MEDIA_BUTTON)
                    .setPackage(pkg)
                    .putExtra(Intent.EXTRA_KEY_EVENT, ev)
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES or Intent.FLAG_RECEIVER_FOREGROUND)
                runCatching { ctx.sendBroadcast(i) }
            }
        }
        if (pkg.contains("spotify", true) && keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
            runCatching {
                ctx.sendBroadcast(Intent("com.spotify.mobile.android.ui.widget.PLAY").setPackage(pkg).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES))
            }
        }
    }
    private fun dispatchGlobalPlay(ctx: Context) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val t = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
    }
}
@Composable
fun NativeMediaAppTile(
    app: AppEntry,
    modifier: Modifier,
    isExpanded: Boolean = false,
    onToggleExpand: (() -> Unit)? = null,
    onOpenWebFallback: (() -> Unit)? = null,
    onChangeApp: () -> Unit,
    onCloseToControls: () -> Unit
) {
    val p = LocalAmni.current
    val ctx = LocalContext.current
    val bridgeNp by MediaSessionBridge.nowPlaying.collectAsState()
    val hasNotifAccess = remember(bridgeNp) { MediaSessionBridge.hasAccess(ctx) }
    val audioArmed by AaAudioStreamer.armed.collectAsState()
    val audioStreaming by AaAudioStreamer.streaming.collectAsState()
    var browser by remember(app.packageName) { mutableStateOf<MediaBrowserCompat?>(null) }
    var ctrl by remember(app.packageName) { mutableStateOf<MediaControllerCompat?>(null) }
    var connected by remember(app.packageName) { mutableStateOf(false) }
    var statusText by remember(app.packageName) { mutableStateOf("Connecting to ${app.label} on phone…") }
    var localTitle by remember(app.packageName) { mutableStateOf("") }
    var localArtist by remember(app.packageName) { mutableStateOf("") }
    var localArt by remember(app.packageName) { mutableStateOf<Bitmap?>(null) }
    var localPlaying by remember(app.packageName) { mutableStateOf(false) }
    val pathStack = remember(app.packageName) { mutableStateListOf<Pair<String, String>>() }
    val items = remember(app.packageName) { mutableStateListOf<BrowseNode>() }
    var showKb by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var voiceStatus by remember { mutableStateOf<String?>(null) }
    fun loadFolder(parentId: String, label: String, push: Boolean) {
        val b = browser ?: return
        if (!b.isConnected) return
        if (push) pathStack.add(parentId to label)
        runCatching { b.unsubscribe(parentId) }
        b.subscribe(parentId, object : MediaBrowserCompat.SubscriptionCallback() {
            override fun onChildrenLoaded(pid: String, children: MutableList<MediaBrowserCompat.MediaItem>) {
                items.clear()
                children.take(37).forEach { mi ->
                    val desc = mi.description
                    items.add(
                        BrowseNode(
                            id = mi.mediaId ?: desc.mediaId.orEmpty(),
                            title = desc.title?.toString().orEmpty().ifBlank { "Untitled" },
                            subtitle = desc.subtitle?.toString().orEmpty(),
                            browsable = mi.isBrowsable,
                            playable = mi.isPlayable
                        )
                    )
                }
                if (items.isNotEmpty()) statusText = "✓ Signed in on phone · ${items.size} library items"
            }
            override fun onError(pid: String) {
                statusText = "✓ Signed in on phone · use Play or Search"
            }
        })
    }
    fun syncFromController(mc: MediaControllerCompat?) {
        val md = mc?.metadata
        if (md != null) {
            localTitle = md.getString(MediaMetadataCompat.METADATA_KEY_TITLE).orEmpty()
            localArtist = (md.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST)).orEmpty()
            localArt = md.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadataCompat.METADATA_KEY_ART) ?: md.getBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON)
        }
        val st = mc?.playbackState?.state
        localPlaying = st == PlaybackStateCompat.STATE_PLAYING || st == PlaybackStateCompat.STATE_BUFFERING
    }
    DisposableEffect(app.packageName) {
        MediaSessionBridge.ensureStarted(ctx)
        val cn = MediaBrowserHub.findBrowserComponent(ctx, app.packageName)
        var bRef: MediaBrowserCompat? = null
        var cRef: MediaControllerCompat? = null
        val mcCb = object : MediaControllerCompat.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadataCompat?) { syncFromController(cRef) }
            override fun onPlaybackStateChanged(state: PlaybackStateCompat?) { syncFromController(cRef) }
        }
        if (cn != null) {
            val cb = object : MediaBrowserCompat.ConnectionCallback() {
                override fun onConnected() {
                    val b = bRef ?: return
                    connected = true
                    statusText = "✓ Signed in on phone"
                    runCatching {
                        val mc = MediaControllerCompat(ctx, b.sessionToken)
                        mc.registerCallback(mcCb)
                        cRef = mc
                        ctrl = mc
                        syncFromController(mc)
                        if (!localPlaying) mc.transportControls.prepare()
                    }.onFailure { Log.w("Amni.MediaHub", "controller attach fail: ${it.message}") }
                    val root = runCatching { b.root }.getOrNull()
                    if (!root.isNullOrBlank()) {
                        pathStack.clear()
                        loadFolder(root, "Library", true)
                    }
                }
                override fun onConnectionSuspended() {
                    connected = false
                    statusText = "Session paused · tap ▶ to wake ${app.label}"
                }
                override fun onConnectionFailed() {
                    connected = false
                    statusText = "Ready via phone session · tap ▶ or 🔎 Search"
                }
            }
            val b = MediaBrowserCompat(ctx.applicationContext, cn, cb, Bundle())
            bRef = b
            browser = b
            runCatching { b.connect() }.onFailure { statusText = "Tap ▶ to start ${app.label}" }
        } else {
            statusText = "✓ Phone session linked · tap ▶ or 🔎 Search"
        }
        onDispose {
            runCatching { cRef?.unregisterCallback(mcCb) }
            runCatching { bRef?.disconnect() }
            ctrl = null
            browser = null
        }
    }
    fun runSongSearch(q: String) {
        val clean = q.trim()
        if (clean.isEmpty()) return
        val mc = ctrl
        if (mc != null) {
            runCatching { mc.transportControls.playFromSearch(clean, Bundle()) }
        } else if (!MediaSessionBridge.playSearch(app.packageName, clean)) {
            MediaBrowserHub.wakeAndPlay(ctx, app.packageName, clean)
        }
        statusText = "▶ Playing “$clean” on ${app.label}"
    }
    fun startVoiceSongSearch() {
        VoiceSearch.listen(
            ctx,
            onState = { voiceStatus = it },
            onResult = { spoken ->
                if (!spoken.isNullOrBlank()) {
                    searchQuery = spoken
                    runSongSearch(spoken)
                    voiceStatus = "Playing “$spoken”"
                    showKb = false
                }
            }
        )
    }
    LaunchedEffect(voiceStatus) {
        val cur = voiceStatus ?: return@LaunchedEffect
        if (!cur.startsWith("Listening") && !cur.startsWith("Hearing") && !cur.startsWith("Processing")) {
            kotlinx.coroutines.delay(3500L)
            if (voiceStatus == cur) voiceStatus = null
        }
    }
    val matchedBridge = bridgeNp?.takeIf { it.pkg.equals(app.packageName, true) }
    val effTitle = localTitle.ifBlank { matchedBridge?.title.orEmpty() }.ifBlank { app.label }
    val effArtist = localArtist.ifBlank { matchedBridge?.artist.orEmpty() }.ifBlank { statusText }
    val effArt = localArt ?: matchedBridge?.art
    val effPlaying = localPlaying || (matchedBridge?.playing == true)
    GlassCard(modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(35.dp)
                    .background(p.panel)
                    .padding(horizontal = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (pathStack.size > 1) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(p.panel2)
                            .border(1.dp, p.hairline, RoundedCornerShape(7.dp))
                            .clickable {
                                pathStack.removeAt(pathStack.lastIndex)
                                val prev = pathStack.last()
                                loadFolder(prev.first, prev.second, false)
                            }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) { Text("◀", color = p.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
                Text(
                    "🎵 ${app.label.uppercase()}",
                    color = p.acc,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (audioArmed) p.accDim else p.panel2)
                        .border(1.dp, if (audioArmed) p.acc else p.hairline, RoundedCornerShape(7.dp))
                        .clickable {
                            if (audioArmed) AaAudioStreamer.stopAll(ctx) else AudioCaptureGrantActivity.launch(ctx)
                        }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (audioStreaming) "🔊 LIVE" else if (audioArmed) "🔊 CAR" else "🔊 BT",
                        color = if (audioArmed) p.acc else p.soft,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (showKb) p.acc else p.panel2)
                        .border(1.dp, if (showKb) p.acc else p.hairline, RoundedCornerShape(7.dp))
                        .clickable { showKb = !showKb }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text(
                        "🔎",
                        color = if (showKb) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink,
                        fontSize = 11.sp
                    )
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (voiceStatus != null) p.acc else p.accDim)
                        .border(1.dp, p.acc.copy(alpha = 0.7f), RoundedCornerShape(7.dp))
                        .clickable { startVoiceSongSearch() }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) { Text("🎤", fontSize = 11.5.sp) }
                if (onOpenWebFallback != null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(p.panel2)
                            .border(1.dp, p.hairline, RoundedCornerShape(7.dp))
                            .clickable { onOpenWebFallback() }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) { Text("WEB", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, fontWeight = FontWeight.Bold) }
                }
                if (onToggleExpand != null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (isExpanded) p.acc else p.panel2)
                            .border(1.dp, if (isExpanded) p.acc else p.hairline, RoundedCornerShape(7.dp))
                            .clickable { onToggleExpand() }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Text(
                            if (isExpanded) "⤡" else "⤢",
                            color = if (isExpanded) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(p.accDim)
                        .border(1.dp, p.acc.copy(alpha = 0.7f), RoundedCornerShape(7.dp))
                        .clickable { onChangeApp() }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) { Text("APP", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(p.panel2)
                        .border(1.dp, p.hairline, RoundedCornerShape(7.dp))
                        .clickable { onCloseToControls() }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) { Text("✕", color = p.ink, fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.fillMaxSize().padding(9.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(p.panel2.copy(alpha = 0.85f))
                            .border(1.dp, p.hairline, RoundedCornerShape(14.dp))
                            .padding(9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(p.accDim)
                                .border(1.dp, p.acc.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (effArt != null) Image(effArt.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Icon(Icons.Rounded.MusicNote, null, Modifier.size(26.dp), tint = p.acc)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(effTitle, color = p.ink, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(effArtist, color = p.soft, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (connected || matchedBridge != null) "✓ SIGNED IN ON PHONE" else "TAP ▶ TO WAKE ${app.label.uppercase()}",
                                color = p.acc,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(p.panel)
                                    .border(1.dp, p.hairline, CircleShape)
                                    .clickable {
                                        val mc = ctrl
                                        if (mc != null) mc.transportControls.skipToPrevious()
                                        else if (!MediaSessionBridge.prev(app.packageName)) MediaBrowserHub.sendTargetedMediaButton(ctx, app.packageName, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                                    },
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Rounded.SkipPrevious, "Prev", Modifier.size(18.dp), tint = p.ink) }
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(p.accentBrush)
                                    .clickable {
                                        val mc = ctrl
                                        if (mc != null) {
                                            if (effPlaying) mc.transportControls.pause() else mc.transportControls.play()
                                        } else if (!MediaSessionBridge.playPausePackage(app.packageName)) {
                                            MediaBrowserHub.wakeAndPlay(ctx, app.packageName)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (effPlaying) "❚❚" else "▶",
                                    color = if (p.isLight) Color.White else Color(0xFF05070A),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(p.panel)
                                    .border(1.dp, p.hairline, CircleShape)
                                    .clickable {
                                        val mc = ctrl
                                        if (mc != null) mc.transportControls.skipToNext()
                                        else if (!MediaSessionBridge.next(app.packageName)) MediaBrowserHub.sendTargetedMediaButton(ctx, app.packageName, KeyEvent.KEYCODE_MEDIA_NEXT)
                                    },
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(18.dp), tint = p.ink) }
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("Liked Songs", "Recently Played", "Chill Mix", "Driving Mix", "Podcasts").forEach { preset ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(p.panel2)
                                    .border(1.dp, p.acc.copy(alpha = 0.45f), RoundedCornerShape(99.dp))
                                    .clickable { runSongSearch(preset) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text("▶ $preset", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (!hasNotifAccess) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(p.accDim)
                                    .border(1.dp, p.acc, RoundedCornerShape(99.dp))
                                    .clickable { MediaSessionBridge.requestAccess(ctx) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text("SYNC TRACK METADATA (PHONE)", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (items.isEmpty()) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(p.panel2.copy(alpha = 0.6f))
                                    .border(1.dp, p.hairline, RoundedCornerShape(12.dp))
                                    .clickable { MediaBrowserHub.wakeAndPlay(ctx, app.packageName) }
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Tap ▶ above, pick a Mix chip, or tap 🔎 / 🎤 to play any song or artist from your signed-in ${app.label} account.", color = p.soft, fontSize = 11.5.sp, lineHeight = 16.sp)
                                    if (!audioArmed) {
                                        Text("No sound on Honda/VW/Ford? Tap [🔊 BT] in the top bar once on your phone to stream audio directly over Amni-Drive USB/Wi-Fi.", color = p.acc, fontSize = 10.5.sp, lineHeight = 14.sp)
                                    }
                                }
                            }
                        } else {
                            items.forEach { node ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(p.panel2.copy(alpha = 0.8f))
                                        .border(1.dp, p.hairline, RoundedCornerShape(10.dp))
                                        .clickable {
                                            if (node.browsable && !node.playable) {
                                                loadFolder(node.id, node.title, true)
                                            } else {
                                                ctrl?.transportControls?.playFromMediaId(node.id, null)
                                                    ?: MediaBrowserHub.wakeAndPlay(ctx, app.packageName, node.title)
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(if (node.browsable && !node.playable) "📁" else "▶", color = p.acc, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Column(Modifier.weight(1f)) {
                                        Text(node.title, color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (node.subtitle.isNotBlank()) Text(node.subtitle, color = p.soft, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
                val hostedKb = BindDashKeyboard(
                    enabled = showKb,
                    spec = com.amni.drive.ui.DashKeyboardSpec(
                        id = "native_media_${app.packageName}",
                        preview = searchQuery,
                        voiceStatus = voiceStatus,
                        placeholder = "Play song, artist, playlist or podcast on ${app.label}…",
                        enterLabel = "PLAY ↵",
                        onText = { searchQuery = (searchQuery + it).take(80) },
                        onBackspace = { searchQuery = searchQuery.dropLast(1) },
                        onClear = { searchQuery = "" },
                        onEnter = {
                            runSongSearch(searchQuery)
                            showKb = false
                        },
                        onVoice = { startVoiceSongSearch() },
                        onClose = { showKb = false }
                    )
                )
                if (showKb && !hostedKb) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable { showKb = false },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        CarTouchKeyboard(
                            preview = searchQuery,
                            voiceStatus = voiceStatus,
                            placeholder = "Play song, artist, playlist or podcast on ${app.label}…",
                            enterLabel = "PLAY ↵",
                            onText = { searchQuery = (searchQuery + it).take(80) },
                            onBackspace = { searchQuery = searchQuery.dropLast(1) },
                            onClear = { searchQuery = "" },
                            onEnter = {
                                runSongSearch(searchQuery)
                                showKb = false
                            },
                            onVoice = { startVoiceSongSearch() },
                            onClose = { showKb = false }
                        )
                    }
                }
            }
        }
    }
}
