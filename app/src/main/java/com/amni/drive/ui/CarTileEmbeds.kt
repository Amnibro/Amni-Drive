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
    onSwitchBuiltIn: () -> Unit
) {
    val p = LocalAmni.current
    val ctx = LocalContext.current
    val dpi = (LocalDensity.current.density * 160f).roundToInt().coerceIn(160, 420)
    val directReady by CarApps.ready.collectAsState()
    var surfaceInfo by remember { mutableStateOf<Triple<Surface, Int, Int>?>(null) }

    LaunchedEffect(Unit) { CarApps.probe() }

    LaunchedEffect(directReady, surfaceInfo) {
        val info = surfaceInfo
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

    GlassCard(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
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
                                CarApps.surface(context, CarApps.MAP, null, 0, 0, dpi)
                                CarEmbed.surface(null, 0, 0, dpi)
                                surf?.release()
                                surf = null
                                return true
                            }
                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                        }
                    }
                }
            )
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xD8080B10))
                    .border(1.dp, p.acc.copy(alpha = 0.65f), RoundedCornerShape(99.dp))
                    .clickable { onSwitchBuiltIn() }
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(
                    "AMNI-MAP · SWITCH",
                    color = p.acc,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
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
    onChangeApp: () -> Unit,
    onCloseToControls: () -> Unit
) {
    val p = LocalAmni.current
    val ctx = LocalContext.current
    val dpi = (LocalDensity.current.density * 160f).roundToInt().coerceIn(160, 420)
    val directReady by CarApps.ready.collectAsState()
    val mirrorLive by CarMirror.live.collectAsState()
    var optInMirror by remember(app.packageName) { mutableStateOf(false) }
    var surfaceInfo by remember { mutableStateOf<Triple<Surface, Int, Int>?>(null) }

    LaunchedEffect(app.packageName) { CarApps.probe() }

    LaunchedEffect(app.packageName, directReady, surfaceInfo, optInMirror) {
        val info = surfaceInfo
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
                                    CarApps.surface(context, CarApps.MEDIA, null, 0, 0, dpi)
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
    onChange: (List<DashWidget>) -> Unit,
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
        SoftSurface(Modifier.fillMaxWidth(0.74f).padding(10.dp), radius = 18.dp) {
            Column(
                Modifier
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "THIRD-ROW WIDGETS",
                        color = p.acc,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "DONE ✕",
                        color = p.ink,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onClose() }.padding(4.dp)
                    )
                }
                Text(
                    "Tap to enable/disable · use ▲ ▼ to reorder",
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
                            .height(44.dp)
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
                                w.label,
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
