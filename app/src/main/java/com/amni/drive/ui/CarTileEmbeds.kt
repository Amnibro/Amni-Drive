package com.amni.drive.ui

import android.graphics.SurfaceTexture
import android.view.KeyEvent
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.amni.drive.launcher.AppEntry
import com.amni.drive.launcher.CarApps
import com.amni.drive.launcher.CarEmbed
import com.amni.drive.launcher.CarMirror
import kotlin.math.roundToInt

/**
 * Live embedded Amni-Map tile for the car dashboard.
 * Uses CarApps (ShellAngel virtual display) when ready, or CarEmbed (DASH_INPUT Messenger surface).
 */
@Composable
fun AmniMapEmbedTile(
    modifier: Modifier,
    compactOrb: Boolean = false,
    isExpanded: Boolean = false,
    onToggleExpand: (() -> Unit)? = null,
    onSwitchBuiltIn: () -> Unit
) {
    val p = LocalAmni.current
    val ctx = LocalContext.current
    val fallbackDpi = (LocalDensity.current.density * 160f).roundToInt().coerceIn(150, 220)
    val directReady by CarApps.ready.collectAsState()
    var surfaceInfo by remember { mutableStateOf<Triple<Surface, Int, Int>?>(null) }

    fun adaptiveDpi(w: Int, h: Int): Int {
        val m = minOf(w, h).coerceAtLeast(180)
        return if (compactOrb) {
            (m * 0.23f).roundToInt().coerceIn(135, 170)
        } else {
            (m * 0.28f).roundToInt().coerceIn(150, 215)
        }
    }

    LaunchedEffect(Unit) { CarApps.probe() }

    LaunchedEffect(directReady, surfaceInfo, compactOrb) {
        val info = surfaceInfo
        val dpi = if (info != null) adaptiveDpi(info.second, info.third) else fallbackDpi
        if (directReady) {
            if (info != null) CarApps.surface(ctx, CarApps.MAP, info.first, info.second, info.third, dpi)
            CarApps.open(ctx, CarApps.MAP, CarEmbed.MAP_PKG)
        } else {
            CarEmbed.attach(ctx, CarEmbed.MAP_PKG)
            if (info != null) CarEmbed.surface(info.first, info.second, info.third, dpi)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            CarEmbed.area(null)
            CarApps.area(CarApps.MAP, null)
        }
    }

    val mapSurfaceBox: @Composable (Modifier) -> Unit = { boxMod ->
        Box(
            boxMod
                .background(Color.Black)
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInRoot()
                    val sz = coords.size
                    val rect = intArrayOf(
                        pos.x.roundToInt(),
                        pos.y.roundToInt(),
                        (pos.x + sz.width).roundToInt(),
                        (pos.y + sz.height).roundToInt()
                    )
                    if (directReady) CarApps.area(CarApps.MAP, rect) else CarEmbed.area(rect)
                }
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    TextureView(context).apply {
                        isOpaque = true
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            private var surf: Surface? = null
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                surf?.release()
                                val s = Surface(st)
                                surf = s
                                surfaceInfo = Triple(s, w, h)
                            }
                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                                val s = surf ?: Surface(st).also { surf = it }
                                surfaceInfo = Triple(s, w, h)
                            }
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                surfaceInfo = null
                                CarApps.surface(context, CarApps.MAP, null, 0, 0, fallbackDpi)
                                CarEmbed.surface(null, 0, 0, fallbackDpi)
                                surf?.release()
                                surf = null
                                return true
                            }
                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                        }
                    }
                }
            )
        }
    }

    if (compactOrb) {
        mapSurfaceBox(modifier.fillMaxSize())
    } else {
        GlassCard(modifier) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .background(p.panel)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        "🗺 AMNI-MAP",
                        color = p.acc,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(p.panel2)
                            .border(1.dp, p.hairline, RoundedCornerShape(6.dp))
                            .clickable { onSwitchBuiltIn() }
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "BUILT-IN",
                            color = p.soft,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (onToggleExpand != null) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isExpanded) p.acc else p.panel2)
                                .border(1.dp, if (isExpanded) p.acc else p.hairline, RoundedCornerShape(6.dp))
                                .clickable { onToggleExpand() }
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                if (isExpanded) "⤡ EXIT" else "⤢",
                                color = if (isExpanded) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                mapSurfaceBox(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

/**
 * Live embedded app tile for the Media box (Spotify, YouTube, Netflix, Ace Hardware, or ANY installed app).
 * Runs directly on its own VirtualDisplay inside the car box via CarApps (ShellAngel) — never touches the phone
 * screen and never triggers MediaProjection screenshare automatically.
 */
@Composable
fun EmbeddedAppMediaTile(
    app: AppEntry,
    modifier: Modifier,
    isExpanded: Boolean = false,
    onToggleExpand: (() -> Unit)? = null,
    onChangeApp: () -> Unit,
    onCloseToControls: () -> Unit
) {
    val p = LocalAmni.current
    val ctx = LocalContext.current
    val fallbackDpi = (LocalDensity.current.density * 160f).roundToInt().coerceIn(160, 240)
    val directReady by CarApps.ready.collectAsState()
    val mirrorLive by CarMirror.live.collectAsState()
    var optInMirror by remember(app.packageName) { mutableStateOf(false) }
    var surfaceInfo by remember { mutableStateOf<Triple<Surface, Int, Int>?>(null) }

    LaunchedEffect(app.packageName) { CarApps.probe() }

    LaunchedEffect(app.packageName, directReady, surfaceInfo, optInMirror) {
        val info = surfaceInfo
        val dpi = if (info != null) (minOf(info.second, info.third) * 0.30f).roundToInt().coerceIn(160, 240) else fallbackDpi
        if (directReady) {
            if (info != null) CarApps.surface(ctx, CarApps.MEDIA, info.first, info.second, info.third, dpi)
            CarApps.open(ctx, CarApps.MEDIA, app.packageName)
        } else if (optInMirror) {
            if (info != null) CarMirror.surface(info.first, info.second, info.third)
            CarMirror.open(ctx, app.packageName)
        }
    }

    DisposableEffect(app.packageName) {
        onDispose {
            CarMirror.area = null
            CarApps.area(CarApps.MEDIA, null)
        }
    }

    GlassCard(modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(p.panel)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(p.panel2)
                        .border(1.dp, p.hairline, RoundedCornerShape(6.dp))
                        .clickable {
                            if (directReady) {
                                CarApps.key(CarApps.MEDIA, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
                                CarApps.key(CarApps.MEDIA, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
                            } else if (optInMirror) {
                                CarMirror.back()
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("◀", color = p.ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    app.label,
                    color = p.ink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (onToggleExpand != null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isExpanded) p.acc else p.panel2)
                            .border(1.dp, if (isExpanded) p.acc else p.hairline, RoundedCornerShape(6.dp))
                            .clickable { onToggleExpand() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (isExpanded) "⤡" else "⤢",
                            color = if (isExpanded) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(p.accDim)
                        .border(1.dp, p.acc.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .clickable { onChangeApp() }
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text("APP", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(p.panel2)
                        .border(1.dp, p.hairline, RoundedCornerShape(6.dp))
                        .clickable { onCloseToControls() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("♪", color = p.ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInRoot()
                        val sz = coords.size
                        val rect = intArrayOf(
                            pos.x.roundToInt(),
                            pos.y.roundToInt(),
                            (pos.x + sz.width).roundToInt(),
                            (pos.y + sz.height).roundToInt()
                        )
                        if (directReady) CarApps.area(CarApps.MEDIA, rect)
                        else if (optInMirror) CarMirror.area = rect
                    }
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        TextureView(context).apply {
                            isOpaque = true
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                private var surf: Surface? = null
                                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                    surf?.release()
                                    val s = Surface(st)
                                    surf = s
                                    surfaceInfo = Triple(s, w, h)
                                }
                                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                                    val s = surf ?: Surface(st).also { surf = it }
                                    surfaceInfo = Triple(s, w, h)
                                }
                                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                    surfaceInfo = null
                                    CarApps.surface(context, CarApps.MEDIA, null, 0, 0, fallbackDpi)
                                    CarMirror.surface(null, 0, 0)
                                    surf?.release()
                                    surf = null
                                    return true
                                }
                                override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                            }
                        }
                    }
                )
                if (!directReady && !(optInMirror && mirrorLive)) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(p.bg.copy(alpha = 0.94f))
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "DIRECT CAR APP ENGINE OFFLINE",
                            color = p.acc,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Start ShellAngel once (Settings → WIRE) to run ${app.label} directly inside the car display without opening on your phone.",
                            color = p.ink,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(p.acc)
                                    .clickable { CarApps.probe() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    "RECONNECT ENGINE",
                                    color = if (p.isLight) Color.White else Color(0xFF05070A),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(p.panel2)
                                    .border(1.dp, p.hairline, RoundedCornerShape(99.dp))
                                    .clickable { optInMirror = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    "MIRROR INSTEAD",
                                    color = p.soft,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive picker to toggle and reorder the third-column dashboard widgets right on the dash.
 */
@Composable
fun WidgetPickerSheet(
    active: List<DashWidget>,
    widthMode: Int = 1,
    compactCards: Boolean = true,
    onChange: (List<DashWidget>) -> Unit,
    onWidthMode: (Int) -> Unit = {},
    onCompactCards: (Boolean) -> Unit = {},
    onClose: () -> Unit
) {
    val p = LocalAmni.current
    var list by remember(active) { mutableStateOf(active) }

    Box(
        Modifier
            .fillMaxSize()
            .background(p.scrim)
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        SoftSurface(
            Modifier
                .fillMaxWidth(0.80f)
                .padding(10.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) {},
            radius = 18.dp
        ) {
            Column(
                Modifier
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "WIDGET STUDIO (${list.size} ON)",
                        color = p.acc,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(p.panel2)
                            .border(1.dp, p.hairline, RoundedCornerShape(8.dp))
                            .clickable {
                                list = DashWidget.DEFAULTS
                                onChange(DashWidget.DEFAULTS)
                                onWidthMode(1)
                                onCompactCards(true)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) { Text("RESET", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                    Text(
                        "DONE ✕",
                        color = p.ink,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onClose() }.padding(4.dp)
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("WIDTH", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    listOf("SLIM" to 0, "NORMAL" to 1, "WIDE" to 2).forEach { (lbl, m) ->
                        val on = widthMode == m
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (on) p.acc else p.panel2)
                                .border(1.dp, if (on) p.acc else p.hairline, RoundedCornerShape(8.dp))
                                .clickable { onWidthMode(m) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(lbl, color = if (on) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("CARDS", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    listOf("COMPACT" to true, "COZY" to false).forEach { (lbl, c) ->
                        val on = compactCards == c
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (on) p.acc else p.panel2)
                                .border(1.dp, if (on) p.acc else p.hairline, RoundedCornerShape(8.dp))
                                .clickable { onCompactCards(c) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(lbl, color = if (on) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    "Tap any widget to toggle ON/OFF · use ▲ ▼ to reorder slots",
                    color = p.soft,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
                val ordered = list + DashWidget.entries.filter { it !in list }
                ordered.forEach { w ->
                    val idx = list.indexOf(w)
                    val on = idx >= 0
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) p.accDim else p.panel2.copy(alpha = 0.65f))
                            .border(1.dp, if (on) p.acc.copy(alpha = 0.65f) else p.hairline, RoundedCornerShape(12.dp))
                            .clickable {
                                val next = if (on) {
                                    if (list.size > 1) list - w else list
                                } else {
                                    list + w
                                }
                                list = next
                                onChange(next)
                            }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(w.emoji, fontSize = 16.sp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (on) "#${idx + 1} · " else "") + w.label,
                                color = if (on) p.acc else p.ink,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(w.blurb, color = p.soft, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (on) {
                            if (idx > 0) {
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(p.panel2)
                                        .clickable {
                                            val m = list.toMutableList()
                                            m[idx] = m[idx - 1].also { m[idx - 1] = m[idx] }
                                            list = m
                                            onChange(m)
                                        },
                                    contentAlignment = Alignment.Center
                                ) { Text("▲", color = p.ink, fontSize = 10.sp) }
                            }
                            if (idx < list.lastIndex) {
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(p.panel2)
                                        .clickable {
                                            val m = list.toMutableList()
                                            m[idx] = m[idx + 1].also { m[idx + 1] = m[idx] }
                                            list = m
                                            onChange(m)
                                        },
                                    contentAlignment = Alignment.Center
                                ) { Text("▼", color = p.ink, fontSize = 10.sp) }
                            }
                            Text("ON", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text("OFF", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
}

data class WebAppEntry(
    val id: String,
    val label: String,
    val emoji: String,
    val url: String,
    val pkgHint: String = ""
) {
    companion object {
        val CATALOG = listOf(
            WebAppEntry("youtube", "YouTube", "▶️", "https://m.youtube.com", "youtube"),
            WebAppEntry("ytmusic", "YT Music", "🎵", "https://music.youtube.com", "youtube.music"),
            WebAppEntry("spotify", "Spotify Web", "🎧", "https://open.spotify.com", "spotify"),
            WebAppEntry("netflix", "Netflix", "🎬", "https://www.netflix.com", "netflix"),
            WebAppEntry("twitch", "Twitch", "📺", "https://m.twitch.tv", "twitch"),
            WebAppEntry("plex", "Plex", "🍿", "https://app.plex.tv/desktop", "plex"),
            WebAppEntry("soundcloud", "SoundCloud", "☁️", "https://m.soundcloud.com", "soundcloud"),
            WebAppEntry("radio", "Radio Garden", "📻", "https://radio.garden", "radio"),
            WebAppEntry("ace", "Ace Hardware", "🛠️", "https://www.acehardware.com", "acehardware"),
            WebAppEntry("google", "Web Search", "🌐", "https://www.google.com", "chrome")
        )

        fun matchPackage(pkg: String): WebAppEntry? {
            val low = pkg.lowercase()
            return CATALOG.firstOrNull { it.pkgHint.isNotBlank() && low.contains(it.pkgHint) }
        }
    }
}

private fun injectWebText(wv: android.webkit.WebView?, text: String) {
    if (wv == null) return
    val escaped = text.replace("\\", "\\\\").replace("'", "\\'")
    val js = """
        (function(){
          var el = document.activeElement;
          if (!el || (el.tagName !== 'INPUT' && el.tagName !== 'TEXTAREA' && !el.isContentEditable)) {
            el = document.querySelector('input[type="search"],input[name="search_query"],input[type="text"],input[type="email"],input[type="password"],input:not([type="hidden"]),textarea');
            if (el) el.focus();
          }
          if (!el) return;
          if (el.isContentEditable) {
            document.execCommand('insertText', false, '$escaped');
          } else {
            var s = el.selectionStart != null ? el.selectionStart : el.value.length;
            var e = el.selectionEnd != null ? el.selectionEnd : el.value.length;
            var nv = el.value.slice(0, s) + '$escaped' + el.value.slice(e);
            var proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
            var setter = Object.getOwnPropertyDescriptor(proto, 'value');
            if (setter && setter.set) setter.set.call(el, nv); else el.value = nv;
            try { el.setSelectionRange(s + '$escaped'.length, s + '$escaped'.length); } catch(_){}
            el.dispatchEvent(new Event('input', {bubbles:true}));
            el.dispatchEvent(new Event('change', {bubbles:true}));
          }
        })();
    """.trimIndent()
    wv.evaluateJavascript(js, null)
}

private fun injectWebBackspace(wv: android.webkit.WebView?) {
    if (wv == null) return
    val js = """
        (function(){
          var el = document.activeElement;
          if (!el) return;
          if (el.isContentEditable) {
            document.execCommand('delete', false, null);
          } else if (el.value != null && el.value.length > 0) {
            var s = el.selectionStart != null ? el.selectionStart : el.value.length;
            var e = el.selectionEnd != null ? el.selectionEnd : el.value.length;
            var nv = s === e && s > 0 ? el.value.slice(0, s - 1) + el.value.slice(e) : el.value.slice(0, s) + el.value.slice(e);
            var pos = s === e && s > 0 ? s - 1 : s;
            var proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
            var setter = Object.getOwnPropertyDescriptor(proto, 'value');
            if (setter && setter.set) setter.set.call(el, nv); else el.value = nv;
            try { el.setSelectionRange(pos, pos); } catch(_){}
            el.dispatchEvent(new Event('input', {bubbles:true}));
          }
        })();
    """.trimIndent()
    wv.evaluateJavascript(js, null)
}

private fun injectWebEnter(wv: android.webkit.WebView?) {
    if (wv == null) return
    val js = """
        (function(){
          var el = document.activeElement;
          if (!el) return;
          ['keydown','keypress','keyup'].forEach(function(t){
            el.dispatchEvent(new KeyboardEvent(t, {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true}));
          });
          if (el.form) {
            if (el.form.requestSubmit) el.form.requestSubmit(); else el.form.submit();
          }
        })();
    """.trimIndent()
    wv.evaluateJavascript(js, null)
    runCatching {
        wv.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        wv.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }
}

/**
 * Zero-ADB, zero-screenshare, native in-car WebView media/app box with built-in car touch keyboard
 * and shared CookieManager sessions from WebLoginActivity.
 */
@android.annotation.SuppressLint("SetJavaScriptEnabled")
@Composable
fun EmbeddedWebMediaTile(
    app: WebAppEntry,
    modifier: Modifier,
    isExpanded: Boolean = false,
    onToggleExpand: (() -> Unit)? = null,
    onChangeApp: () -> Unit,
    onCloseToControls: () -> Unit
) {
    val p = LocalAmni.current
    val ctx = LocalContext.current
    var webRef by remember { mutableStateOf<android.webkit.WebView?>(null) }
    var pageTitle by remember(app.id) { mutableStateOf(app.label) }
    var showKb by remember { mutableStateOf(false) }
    var symMode by remember { mutableStateOf(false) }
    var loginBanner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(loginBanner) {
        if (loginBanner != null) {
            kotlinx.coroutines.delay(4000)
            loginBanner = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                android.webkit.CookieManager.getInstance().flush()
                webRef?.onPause()
                webRef?.destroy()
            }
            webRef = null
        }
    }

    GlassCard(modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(p.panel)
                    .padding(horizontal = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(p.panel2)
                        .border(1.dp, p.hairline, RoundedCornerShape(6.dp))
                        .clickable { if (webRef?.canGoBack() == true) webRef?.goBack() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) { Text("◀", color = p.ink, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(p.panel2)
                        .border(1.dp, p.hairline, RoundedCornerShape(6.dp))
                        .clickable { webRef?.reload() }
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) { Text("↻", color = p.ink, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                Text(
                    "${app.emoji} $pageTitle",
                    color = p.ink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (showKb) p.acc else p.panel2)
                        .border(1.dp, if (showKb) p.acc else p.hairline, RoundedCornerShape(6.dp))
                        .clickable { showKb = !showKb }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "⌨",
                        color = if (showKb) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(p.panel2)
                        .border(1.dp, p.hairline, RoundedCornerShape(6.dp))
                        .clickable {
                            val ok = WebLoginActivity.open(ctx, webRef?.url ?: app.url, app.label)
                            loginBanner = if (ok) {
                                "🔑 Opened ${app.label} sign-in on phone · tap SAVE & SYNC, then ↻"
                            } else {
                                "🔑 Unlock phone → Amni-Drive → Settings → WIRE → Sign-Ins"
                            }
                        }
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) { Text("🔑", fontSize = 9.sp) }
                if (onToggleExpand != null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isExpanded) p.acc else p.panel2)
                            .border(1.dp, if (isExpanded) p.acc else p.hairline, RoundedCornerShape(6.dp))
                            .clickable { onToggleExpand() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (isExpanded) "⤡" else "⤢",
                            color = if (isExpanded) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(p.accDim)
                        .border(1.dp, p.acc.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .clickable { onChangeApp() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) { Text("APP", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(p.panel2)
                        .border(1.dp, p.hairline, RoundedCornerShape(6.dp))
                        .clickable { onCloseToControls() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) { Text("♪", color = p.ink, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            }

            loginBanner?.let { banner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(p.accDim)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        banner,
                        color = p.acc,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                }
            }

            AndroidView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                factory = { c ->
                    android.webkit.WebView(c).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.userAgentString = WebLoginActivity.cleanChromeUserAgent(settings.userAgentString)
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        runCatching {
                            val cm = android.webkit.CookieManager.getInstance()
                            cm.setAcceptCookie(true)
                            cm.setAcceptThirdPartyCookies(this, true)
                        }
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                runCatching { android.webkit.CookieManager.getInstance().flush() }
                                val t = view?.title?.takeIf { it.isNotBlank() }
                                if (t != null) pageTitle = t
                            }
                        }
                        webChromeClient = android.webkit.WebChromeClient()
                        loadUrl(app.url)
                        webRef = this
                    }
                },
                update = { wv ->
                    if (wv.tag != app.url) {
                        wv.tag = app.url
                        wv.loadUrl(app.url)
                    }
                }
            )

            if (showKb) {
                val rows = if (!symMode) {
                    listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
                } else {
                    listOf("1234567890", "@._-/:;?!", "+=&%#()'")
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(p.panel.copy(alpha = 0.98f))
                        .border(1.dp, p.hairline)
                        .padding(3.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    rows.forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            row.forEach { ch ->
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(26.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(p.panel2)
                                        .border(1.dp, p.hairline, RoundedCornerShape(5.dp))
                                        .clickable { injectWebText(webRef, ch.toString()) },
                                    contentAlignment = Alignment.Center
                                ) { Text(ch.toString(), color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(
                            Modifier.weight(1.2f).height(26.dp).clip(RoundedCornerShape(5.dp)).background(p.panel2).border(1.dp, p.hairline, RoundedCornerShape(5.dp)).clickable { symMode = !symMode },
                            contentAlignment = Alignment.Center
                        ) { Text(if (symMode) "ABC" else "123", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                        Box(
                            Modifier.weight(1.1f).height(26.dp).clip(RoundedCornerShape(5.dp)).background(p.panel2).border(1.dp, p.hairline, RoundedCornerShape(5.dp)).clickable { injectWebText(webRef, ".com") },
                            contentAlignment = Alignment.Center
                        ) { Text(".com", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                        Box(
                            Modifier.weight(2.6f).height(26.dp).clip(RoundedCornerShape(5.dp)).background(p.panel2).border(1.dp, p.hairline, RoundedCornerShape(5.dp)).clickable { injectWebText(webRef, " ") },
                            contentAlignment = Alignment.Center
                        ) { Text("SPACE", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                        Box(
                            Modifier.weight(1.1f).height(26.dp).clip(RoundedCornerShape(5.dp)).background(p.panel2).border(1.dp, p.hairline, RoundedCornerShape(5.dp)).clickable { injectWebBackspace(webRef) },
                            contentAlignment = Alignment.Center
                        ) { Text("⌫", color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        Box(
                            Modifier.weight(1.3f).height(26.dp).clip(RoundedCornerShape(5.dp)).background(p.acc).clickable { injectWebEnter(webRef); showKb = false },
                            contentAlignment = Alignment.Center
                        ) { Text("GO ↵", color = if (p.isLight) Color.White else Color(0xFF05070A), fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}
