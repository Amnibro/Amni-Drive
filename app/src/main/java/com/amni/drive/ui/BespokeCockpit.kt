package com.amni.drive.ui
import android.content.Context
import android.location.Location
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amni.drive.config.DriveConfig
import com.amni.drive.gate.AppCategory
import com.amni.drive.launcher.AppEntry
import com.amni.drive.map.RouteResult
import com.amni.drive.media.MediaBrowserHub
import com.amni.drive.media.MediaSessionBridge
import com.amni.drive.update.OtaUpdater
import com.amni.drive.voice.VoiceSearch
import com.amni.drive.weather.WeatherNow
import com.amni.drive.wire.AaAudioStreamer
import com.amni.drive.wire.AudioCaptureGrantActivity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
fun buildChamferPath(size: Size, cut: Float): Path = Path().apply {
    val c = cut.coerceAtMost(min(size.width, size.height) * 0.34f)
    moveTo(c, 0f)
    lineTo(size.width - c, 0f)
    lineTo(size.width, c)
    lineTo(size.width, size.height - c)
    lineTo(size.width - c, size.height)
    lineTo(c, size.height)
    lineTo(0f, size.height - c)
    lineTo(0f, c)
    close()
}
fun buildHexagonPath(size: Size): Path = Path().apply {
    val w = size.width
    val h = size.height
    moveTo(w * 0.5f, 0f)
    lineTo(w, h * 0.25f)
    lineTo(w, h * 0.75f)
    lineTo(w * 0.5f, h)
    lineTo(0f, h * 0.75f)
    lineTo(0f, h * 0.25f)
    close()
}
class ChamferPolygonShape(private val cutDp: Dp = 22.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(buildChamferPath(size, with(density) { cutDp.toPx() }))
}
object HexagonCellShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(buildHexagonPath(size))
}
@Composable
fun ChamferPod(
    modifier: Modifier = Modifier,
    cutDp: Dp = 22.dp,
    accentGlow: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val p = LocalAmni.current
    val shape = remember(cutDp) { ChamferPolygonShape(cutDp) }
    val density = LocalDensity.current
    val cutPx = with(density) { cutDp.toPx() }
    val strokePx = with(density) { 1.3.dp.toPx() }
    val bracketPx = with(density) { 2.4.dp.toPx() }
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(p.glassA, p.glassB)), shape)
    ) {
        content()
        Canvas(Modifier.fillMaxSize()) {
            val path = buildChamferPath(size, cutPx)
            drawPath(path, color = if (accentGlow) p.acc.copy(alpha = 0.65f) else p.line2.copy(alpha = 0.72f), style = Stroke(width = strokePx))
            val c = cutPx.coerceAtMost(min(size.width, size.height) * 0.34f)
            val arm = c * 0.75f
            val br = Path().apply {
                moveTo(0f, c + arm); lineTo(0f, c); lineTo(c, 0f); lineTo(c + arm, 0f)
                moveTo(size.width - c - arm, 0f); lineTo(size.width - c, 0f); lineTo(size.width, c); lineTo(size.width, c + arm)
                moveTo(size.width, size.height - c - arm); lineTo(size.width, size.height - c); lineTo(size.width - c, size.height); lineTo(size.width - c - arm, size.height)
                moveTo(c + arm, size.height); lineTo(c, size.height); lineTo(0f, size.height - c); lineTo(0f, size.height - c - arm)
            }
            drawPath(br, color = p.acc.copy(alpha = 0.85f), style = Stroke(width = bracketPx, cap = StrokeCap.Round))
        }
    }
}
@Composable
private fun PolarCrownButton(
    radiusPx: Float,
    angleDeg: Float,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val p = LocalAmni.current
    val rad = angleDeg * (PI.toFloat() / 180f)
    val dx = (radiusPx * cos(rad)).roundToInt()
    val dy = (radiusPx * sin(rad)).roundToInt()
    Box(
        Modifier
            .offset { IntOffset(dx, dy) }
            .size(34.dp)
            .shadow(7.dp, CircleShape)
            .clip(CircleShape)
            .background(if (active) p.acc else p.panel.copy(alpha = 0.96f))
            .border(1.3.dp, if (active) p.acc else p.acc.copy(alpha = 0.65f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (active) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
@Composable
fun BespokeCircularMapPod(
    modifier: Modifier,
    location: Location?,
    speedMph: Int,
    route: RouteResult?,
    hasAmniMap: Boolean,
    useAmniMap: Boolean,
    carSurface: Boolean,
    onOpenSearch: () -> Unit,
    onVoice: (() -> Unit)?,
    onOpenStudio: () -> Unit,
    onToggleEngine: () -> Unit,
    onExpand: () -> Unit,
    mapContent: @Composable (Modifier, Boolean) -> Unit
) {
    val p = LocalAmni.current
    val density = LocalDensity.current
    val bearing = location?.takeIf { it.hasBearing() }?.bearing ?: 0f
    val degInt = ((bearing.roundToInt() % 360) + 360) % 360
    val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val cardinal = if (location != null && location.hasBearing()) dirs[((degInt + 22) / 45) % 8] else "N"
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val dialDp = (minOf(maxWidth, maxHeight) - 4.dp).coerceAtLeast(154.dp)
        val bezelPad = (dialDp * 0.068f).coerceIn(11.dp, 20.dp)
        val crownRadiusPx = with(density) { ((dialDp * 0.5f) - (bezelPad * 0.42f)).toPx() }
        Box(Modifier.size(dialDp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension * 0.5f
                val cx = size.width * 0.5f
                val cy = size.height * 0.5f
                drawCircle(
                    brush = Brush.radialGradient(listOf(p.panel2, p.panel, p.bg)),
                    radius = r,
                    center = Offset(cx, cy)
                )
                drawCircle(color = p.acc.copy(alpha = 0.55f), radius = r - 1.5.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 2.dp.toPx()))
                val tickOuter = r - 4.dp.toPx()
                for (i in 0 until 72) {
                    val deg = i * 5f - bearing - 90f
                    val rad = deg * (PI.toFloat() / 180f)
                    val cardinalTick = i % 18 == 0
                    val majorTick = i % 6 == 0
                    val len = when {
                        cardinalTick -> 8.dp.toPx()
                        majorTick -> 5.5.dp.toPx()
                        else -> 3.dp.toPx()
                    }
                    val col = when {
                        i == 0 -> p.acc
                        cardinalTick -> p.ink.copy(alpha = 0.85f)
                        majorTick -> p.soft.copy(alpha = 0.55f)
                        else -> p.dim.copy(alpha = 0.32f)
                    }
                    val sw = if (cardinalTick) 2.2.dp.toPx() else if (majorTick) 1.3.dp.toPx() else 0.9.dp.toPx()
                    drawLine(
                        color = col,
                        start = Offset(cx + (tickOuter - len) * cos(rad), cy + (tickOuter - len) * sin(rad)),
                        end = Offset(cx + tickOuter * cos(rad), cy + tickOuter * sin(rad)),
                        strokeWidth = sw,
                        cap = StrokeCap.Round
                    )
                }
                val ptr = Path().apply {
                    moveTo(cx, cy - r + 13.dp.toPx())
                    lineTo(cx - 5.dp.toPx(), cy - r + 4.dp.toPx())
                    lineTo(cx + 5.dp.toPx(), cy - r + 4.dp.toPx())
                    close()
                }
                drawPath(ptr, color = p.acc)
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(bezelPad)
                    .shadow(10.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .border(1.4.dp, p.acc.copy(alpha = 0.48f), CircleShape)
            ) {
                mapContent(Modifier.fillMaxSize(), true)
            }
            Box(
                Modifier
                    .offset { IntOffset(0, -crownRadiusPx.roundToInt()) }
                    .clip(RoundedCornerShape(99.dp))
                    .background(p.panel.copy(alpha = 0.95f))
                    .border(1.dp, p.acc.copy(alpha = 0.7f), RoundedCornerShape(99.dp))
                    .clickable { if (hasAmniMap && carSurface) onToggleEngine() else onOpenStudio() }
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(
                    if (hasAmniMap && carSurface) (if (useAmniMap) "3D MAP · $cardinal" else "LITE · $cardinal") else "HDG · $cardinal",
                    color = p.acc,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            PolarCrownButton(crownRadiusPx, -138f, "🔎", onClick = onOpenSearch)
            if (onVoice != null) PolarCrownButton(crownRadiusPx, -42f, "🎤", onClick = onVoice)
            PolarCrownButton(crownRadiusPx, 142f, "⚙", onClick = onOpenStudio)
            PolarCrownButton(crownRadiusPx, 38f, "⤢", onClick = onExpand)
            Box(
                Modifier
                    .offset { IntOffset(0, crownRadiusPx.roundToInt()) }
                    .clip(RoundedCornerShape(99.dp))
                    .background(p.panel.copy(alpha = 0.96f))
                    .border(1.2.dp, p.acc.copy(alpha = 0.65f), RoundedCornerShape(99.dp))
                    .padding(horizontal = 11.dp, vertical = 3.dp)
            ) {
                val badge = if (route != null) {
                    "${(route.durationS / 60).roundToInt()}m · %.1f mi".format(route.distanceM / 1609.34)
                } else {
                    "$speedMph MPH · $cardinal"
                }
                Text(badge, color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
private fun dispatchMediaFallbackKey(ctx: Context, code: Int) {
    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    val t = SystemClock.uptimeMillis()
    am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
    am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
}
@Composable
fun BespokeCircularMediaPod(
    modifier: Modifier,
    embeddedMediaApp: AppEntry?,
    embeddedWebApp: WebAppEntry?,
    mediaHint: String?,
    onOpenMediaPicker: () -> Unit,
    onClearMediaApp: () -> Unit,
    onExpand: () -> Unit,
    portholeFallback: @Composable (Modifier, Boolean) -> Unit
) {
    val p = LocalAmni.current
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val np by MediaSessionBridge.nowPlaying.collectAsState()
    val audioArmed by AaAudioStreamer.armed.collectAsState()
    val audioStreaming by AaAudioStreamer.streaming.collectAsState()
    var showSongKb by remember { mutableStateOf(false) }
    var songQuery by remember { mutableStateOf("") }
    var voiceStatus by remember { mutableStateOf<String?>(null) }
    val isNativeAudio = embeddedWebApp == null && (embeddedMediaApp == null || MediaBrowserHub.isAudioApp(ctx, embeddedMediaApp.packageName) || embeddedMediaApp.category == AppCategory.MEDIA)
    val activePkg = embeddedMediaApp?.packageName ?: np?.pkg ?: "com.spotify.music"
    val appBadge = embeddedMediaApp?.label ?: np?.appLabel?.takeIf { it.isNotBlank() } ?: mediaHint ?: "Spotify / Audio"
    val playing = np?.playing == true
    val trackTitle = np?.title?.takeIf { it.isNotBlank() } ?: embeddedMediaApp?.let { "Ready · ${it.label}" } ?: "Tap ▶ or 🔎 Song"
    val trackSub = np?.artist?.takeIf { it.isNotBlank() } ?: (if (embeddedMediaApp != null) "✓ Signed in on phone" else "Tap badge to switch app")
    val appIcon = if (embeddedMediaApp != null) rememberAppIcon(embeddedMediaApp.packageName) else null
    BindDashKeyboard(
        enabled = showSongKb,
        spec = DashKeyboardSpec(
            id = "circular_turntable_search",
            preview = songQuery,
            voiceStatus = voiceStatus,
            placeholder = "Search song, artist, or playlist in $appBadge…",
            enterLabel = "PLAY ↵",
            onText = { songQuery = (songQuery + it).take(64) },
            onBackspace = { songQuery = songQuery.dropLast(1) },
            onClear = { songQuery = "" },
            onEnter = {
                val q = songQuery.trim()
                if (q.isNotEmpty()) MediaBrowserHub.wakeAndPlay(ctx, activePkg, q)
                showSongKb = false
            },
            onVoice = {
                VoiceSearch.listen(ctx, { voiceStatus = it }, { spoken ->
                    if (!spoken.isNullOrBlank()) {
                        songQuery = spoken
                        MediaBrowserHub.wakeAndPlay(ctx, activePkg, spoken.trim())
                        voiceStatus = null
                        showSongKb = false
                    }
                })
            },
            onClose = { showSongKb = false }
        )
    )
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val dialDp = (minOf(maxWidth, maxHeight) - 4.dp).coerceAtLeast(154.dp)
        val bezelPad = (dialDp * 0.068f).coerceIn(11.dp, 20.dp)
        val crownRadiusPx = with(density) { ((dialDp * 0.5f) - (bezelPad * 0.42f)).toPx() }
        if (!isNativeAudio) {
            Box(Modifier.size(dialDp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(bezelPad)
                        .shadow(10.dp, CircleShape)
                        .clip(CircleShape)
                        .background(p.panel)
                        .border(2.dp, p.accentBrush, CircleShape)
                ) {
                    portholeFallback(Modifier.fillMaxSize(), true)
                }
                Box(
                    Modifier
                        .offset { IntOffset(0, -crownRadiusPx.roundToInt()) }
                        .clip(RoundedCornerShape(99.dp))
                        .background(p.panel.copy(alpha = 0.96f))
                        .border(1.dp, p.acc, RoundedCornerShape(99.dp))
                        .clickable { onOpenMediaPicker() }
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(embeddedWebApp?.let { "${it.emoji} ${it.label}" } ?: appBadge, color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                PolarCrownButton(crownRadiusPx, 142f, "✕", onClick = onClearMediaApp)
                PolarCrownButton(crownRadiusPx, 38f, "⤢", onClick = onExpand)
            }
        } else {
            val platterDp = (dialDp * 0.36f).coerceIn(54.dp, 124.dp)
            val skipDp = (dialDp * 0.16f).coerceIn(30.dp, 44.dp)
            val compact = dialDp < 215.dp
            Box(Modifier.size(dialDp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension * 0.5f
                    val cx = size.width * 0.5f
                    val cy = size.height * 0.5f
                    drawCircle(
                        brush = Brush.radialGradient(listOf(p.panel2, p.panel, p.bg)),
                        radius = r,
                        center = Offset(cx, cy)
                    )
                    drawCircle(color = p.line2.copy(alpha = 0.65f), radius = r - 1.5.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 1.6.dp.toPx()))
                    drawArc(
                        color = if (playing) p.acc else p.acc.copy(alpha = 0.35f),
                        startAngle = 135f,
                        sweepAngle = if (playing) 270f else 190f,
                        useCenter = false,
                        topLeft = Offset(cx - r + 5.dp.toPx(), cy - r + 5.dp.toPx()),
                        size = Size((r - 5.dp.toPx()) * 2f, (r - 5.dp.toPx()) * 2f),
                        style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
                    )
                    for (g in 1..4) {
                        drawCircle(
                            color = p.hairline.copy(alpha = 0.22f),
                            radius = r * (0.44f + g * 0.09f),
                            center = Offset(cx, cy),
                            style = Stroke(width = 0.9.dp.toPx())
                        )
                    }
                }
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = dialDp * 0.13f, vertical = dialDp * 0.11f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .widthIn(max = dialDp * 0.46f)
                                .clip(RoundedCornerShape(99.dp))
                                .background(p.accDim)
                                .border(1.dp, p.acc.copy(alpha = 0.65f), RoundedCornerShape(99.dp))
                                .clickable { onOpenMediaPicker() }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("✓ $appBadge", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = if (compact) 8.5.sp else 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (audioArmed) p.accDim else p.panel2)
                                .border(1.dp, if (audioArmed) p.acc else p.hairline, RoundedCornerShape(99.dp))
                                .clickable { if (audioArmed) AaAudioStreamer.stopAll(ctx) else AudioCaptureGrantActivity.launch(ctx) }
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(if (audioStreaming) "🔊 LIVE" else if (audioArmed) "🔊 CAR" else "🔊 BT", color = if (audioArmed) p.acc else p.soft, fontFamily = FontFamily.Monospace, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(skipDp)
                                .clip(CircleShape)
                                .background(p.panel2)
                                .border(1.dp, p.hairline, CircleShape)
                                .clickable {
                                    if (!MediaSessionBridge.prev(embeddedMediaApp?.packageName)) {
                                        if (embeddedMediaApp != null) MediaBrowserHub.sendTargetedMediaButton(ctx, embeddedMediaApp.packageName, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                                        else dispatchMediaFallbackKey(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.SkipPrevious, "Prev", Modifier.size(skipDp * 0.56f), tint = p.ink) }
                        Box(
                            Modifier
                                .size(platterDp)
                                .shadow(9.dp, CircleShape)
                                .clip(CircleShape)
                                .background(p.accDim)
                                .border(2.dp, if (playing) p.acc else p.line2, CircleShape)
                                .clickable {
                                    if (embeddedMediaApp != null) {
                                        if (!MediaSessionBridge.playPausePackage(embeddedMediaApp.packageName)) MediaBrowserHub.wakeAndPlay(ctx, embeddedMediaApp.packageName)
                                    } else if (!MediaSessionBridge.playPause()) {
                                        dispatchMediaFallbackKey(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val art = np?.art
                            if (art != null) Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else if (appIcon != null) Image(appIcon, null, Modifier.size(platterDp * 0.62f).clip(CircleShape))
                            else Icon(Icons.Rounded.MusicNote, null, Modifier.size(platterDp * 0.48f), tint = p.acc)
                            Box(
                                Modifier
                                    .size((platterDp * 0.22f).coerceAtLeast(12.dp))
                                    .clip(CircleShape)
                                    .background(p.bg.copy(alpha = 0.88f))
                                    .border(1.5.dp, p.acc, CircleShape)
                            )
                        }
                        Box(
                            Modifier
                                .size(skipDp)
                                .clip(CircleShape)
                                .background(p.panel2)
                                .border(1.dp, p.hairline, CircleShape)
                                .clickable {
                                    if (!MediaSessionBridge.next(embeddedMediaApp?.packageName)) {
                                        if (embeddedMediaApp != null) MediaBrowserHub.sendTargetedMediaButton(ctx, embeddedMediaApp.packageName, KeyEvent.KEYCODE_MEDIA_NEXT)
                                        else dispatchMediaFallbackKey(ctx, KeyEvent.KEYCODE_MEDIA_NEXT)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(skipDp * 0.56f), tint = p.ink) }
                    }
                    Column(
                        Modifier.widthIn(max = dialDp * 0.72f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(trackTitle, color = p.ink, fontSize = if (compact) 11.5.sp else 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        Text(voiceStatus ?: trackSub, color = if (voiceStatus != null) p.acc else p.soft, fontSize = if (compact) 9.5.sp else 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(if (compact) 29.dp else 34.dp)
                                .clip(CircleShape)
                                .background(if (showSongKb) p.acc else p.panel2)
                                .border(1.dp, p.acc.copy(alpha = 0.65f), CircleShape)
                                .clickable { showSongKb = !showSongKb },
                            contentAlignment = Alignment.Center
                        ) { Text("🔎", fontSize = if (compact) 11.sp else 12.5.sp) }
                        Box(
                            Modifier
                                .size(if (compact) 36.dp else 44.dp)
                                .shadow(6.dp, CircleShape)
                                .clip(CircleShape)
                                .background(p.accentBrush)
                                .clickable {
                                    if (embeddedMediaApp != null) {
                                        if (!MediaSessionBridge.playPausePackage(embeddedMediaApp.packageName)) MediaBrowserHub.wakeAndPlay(ctx, embeddedMediaApp.packageName)
                                    } else if (!MediaSessionBridge.playPause()) {
                                        dispatchMediaFallbackKey(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (playing) "❚❚" else "▶", color = if (p.isLight) Color.White else Color(0xFF05070A), fontSize = if (compact) 13.sp else 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            Modifier
                                .size(if (compact) 29.dp else 34.dp)
                                .clip(CircleShape)
                                .background(if (voiceStatus != null) p.acc else p.accDim)
                                .border(1.dp, p.acc.copy(alpha = 0.65f), CircleShape)
                                .clickable {
                                    VoiceSearch.listen(ctx, { voiceStatus = it }, { spoken ->
                                        if (!spoken.isNullOrBlank()) {
                                            MediaBrowserHub.wakeAndPlay(ctx, activePkg, spoken.trim())
                                            voiceStatus = null
                                        }
                                    })
                                },
                            contentAlignment = Alignment.Center
                        ) { Text("🎤", fontSize = if (compact) 11.sp else 12.5.sp) }
                    }
                }
                PolarCrownButton(crownRadiusPx, 38f, "⤢", onClick = onExpand)
            }
        }
    }
}
@Composable
fun CircularSpeedChrono(speedMph: Int, route: RouteResult?, modifier: Modifier = Modifier) {
    val p = LocalAmni.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val sz = (minOf(maxWidth, maxHeight) - 2.dp).coerceAtLeast(86.dp)
        val compact = sz < 124.dp
        Box(Modifier.size(sz), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension * 0.5f
                val cx = size.width * 0.5f
                val cy = size.height * 0.5f
                drawCircle(brush = Brush.radialGradient(listOf(p.panel2, p.panel)), radius = r, center = Offset(cx, cy))
                drawCircle(color = p.hairline, radius = r - 1.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 1.2.dp.toPx()))
                val arcInset = 6.dp.toPx()
                drawArc(
                    color = p.line.copy(alpha = 0.45f),
                    startAngle = 150f,
                    sweepAngle = 240f,
                    useCenter = false,
                    topLeft = Offset(arcInset, arcInset),
                    size = Size(size.width - arcInset * 2f, size.height - arcInset * 2f),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
                val frac = (speedMph.coerceIn(0, 120) / 120f)
                drawArc(
                    color = p.acc,
                    startAngle = 150f,
                    sweepAngle = (240f * frac).coerceAtLeast(6f),
                    useCenter = false,
                    topLeft = Offset(arcInset, arcInset),
                    size = Size(size.width - arcInset * 2f, size.height - arcInset * 2f),
                    style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
                )
                for (t in 0..12) {
                    val a = (150f + t * 20f) * (PI.toFloat() / 180f)
                    val outer = r - 11.dp.toPx()
                    val inner = outer - (if (t % 3 == 0) 5.dp.toPx() else 2.8.dp.toPx())
                    drawLine(
                        color = if (t % 3 == 0) p.soft else p.dim.copy(alpha = 0.5f),
                        start = Offset(cx + inner * cos(a), cy + inner * sin(a)),
                        end = Offset(cx + outer * cos(a), cy + outer * sin(a)),
                        strokeWidth = if (t % 3 == 0) 1.5.dp.toPx() else 1.dp.toPx()
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$speedMph", color = p.ink, fontFamily = FontFamily.Monospace, fontSize = if (compact) 20.sp else 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (route != null) "${(route.durationS / 60).roundToInt()}m ETA" else "MPH",
                    color = p.acc,
                    fontFamily = FontFamily.Monospace,
                    fontSize = if (compact) 8.5.sp else 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
@Composable
fun CircularWeatherDial(weather: WeatherNow?, modifier: Modifier = Modifier) {
    val p = LocalAmni.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val sz = (minOf(maxWidth, maxHeight) - 2.dp).coerceAtLeast(86.dp)
        val compact = sz < 124.dp
        Box(Modifier.size(sz), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension * 0.5f
                drawCircle(brush = Brush.radialGradient(listOf(p.panel2, p.panel)), radius = r)
                drawCircle(color = p.hairline, radius = r - 1.dp.toPx(), style = Stroke(width = 1.2.dp.toPx()))
                val inset = 6.dp.toPx()
                drawArc(
                    color = p.gold.copy(alpha = 0.65f),
                    startAngle = 195f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2f, size.height - inset * 2f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(weather?.emoji ?: "🌤️", fontSize = if (compact) 16.sp else 22.sp)
                Text("${weather?.tempF ?: "--"}°", color = p.ink, fontFamily = FontFamily.Monospace, fontSize = if (compact) 16.sp else 21.sp, fontWeight = FontWeight.Bold)
                if (!compact) {
                    Text(weather?.label ?: "GPS WX", color = p.soft, fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = sz * 0.7f), textAlign = TextAlign.Center)
                }
            }
        }
    }
}
@Composable
fun CircularRotaryAppsPod(
    allApps: List<AppEntry>,
    onQuickLaunch: (AppEntry) -> Unit,
    onOpenAllApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalAmni.current
    val density = LocalDensity.current
    val picks = remember(allApps) {
        val keys = listOf("spotify", "youtube", "amniscient.map", "music", "netflix", "haven")
        val found = keys.mapNotNull { k -> allApps.firstOrNull { it.packageName.contains(k, true) } }.distinctBy { it.packageName }
        (found + allApps).distinctBy { it.packageName }.take(4)
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val sz = (minOf(maxWidth, maxHeight) - 2.dp).coerceAtLeast(94.dp)
        val puckDp = (sz * 0.25f).coerceIn(26.dp, 42.dp)
        val orbitPx = with(density) { (sz * 0.30f).toPx() }
        Box(Modifier.size(sz), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension * 0.5f
                drawCircle(brush = Brush.radialGradient(listOf(p.panel2, p.panel)), radius = r)
                drawCircle(color = p.acc.copy(alpha = 0.45f), radius = r - 1.5.dp.toPx(), style = Stroke(width = 1.4.dp.toPx()))
                drawCircle(color = p.hairline, radius = orbitPx, style = Stroke(width = 1.dp.toPx()))
            }
            val angles = floatArrayOf(-135f, -45f, 135f, 45f)
            picks.forEachIndexed { idx, app ->
                val rad = angles[idx % angles.size] * (PI.toFloat() / 180f)
                val ox = (orbitPx * cos(rad)).roundToInt()
                val oy = (orbitPx * sin(rad)).roundToInt()
                val icon = rememberAppIcon(app.packageName)
                Box(
                    Modifier
                        .offset { IntOffset(ox, oy) }
                        .size(puckDp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(p.panel)
                        .border(1.dp, p.acc.copy(alpha = 0.55f), CircleShape)
                        .clickable { onQuickLaunch(app) },
                    contentAlignment = Alignment.Center
                ) {
                    if (icon != null) Image(icon, app.label, Modifier.size(puckDp * 0.72f).clip(CircleShape))
                    else Text(app.label.take(1).uppercase(), color = p.acc, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(
                Modifier
                    .size((sz * 0.27f).coerceAtLeast(28.dp))
                    .clip(CircleShape)
                    .background(p.accDim)
                    .border(1.2.dp, p.acc, CircleShape)
                    .clickable { onOpenAllApps() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Apps, "Apps", Modifier.size(16.dp), tint = p.acc)
            }
        }
    }
}
@Composable
fun CapsuleMediaDeck(
    modifier: Modifier,
    embeddedMediaApp: AppEntry?,
    embeddedWebApp: WebAppEntry?,
    mediaHint: String?,
    onOpenMediaPicker: () -> Unit,
    onExpand: () -> Unit,
    fallbackMedia: @Composable (Modifier, Boolean) -> Unit
) {
    val p = LocalAmni.current
    val ctx = LocalContext.current
    val np by MediaSessionBridge.nowPlaying.collectAsState()
    val audioArmed by AaAudioStreamer.armed.collectAsState()
    val audioStreaming by AaAudioStreamer.streaming.collectAsState()
    var showSongKb by remember { mutableStateOf(false) }
    var songQuery by remember { mutableStateOf("") }
    var voiceStatus by remember { mutableStateOf<String?>(null) }
    val isNativeAudio = embeddedWebApp == null && (embeddedMediaApp == null || MediaBrowserHub.isAudioApp(ctx, embeddedMediaApp.packageName) || embeddedMediaApp.category == AppCategory.MEDIA)
    val activePkg = embeddedMediaApp?.packageName ?: np?.pkg ?: "com.spotify.music"
    val appBadge = embeddedMediaApp?.label ?: np?.appLabel?.takeIf { it.isNotBlank() } ?: mediaHint ?: "Spotify"
    val playing = np?.playing == true
    val title = np?.title?.takeIf { it.isNotBlank() } ?: embeddedMediaApp?.let { "✓ ${it.label} Signed In" } ?: "Tap ▶ or 🔎 Song"
    val artist = np?.artist?.takeIf { it.isNotBlank() } ?: "Phone session synced · tap SOURCE to switch"
    val appIcon = if (embeddedMediaApp != null) rememberAppIcon(embeddedMediaApp.packageName) else null
    BindDashKeyboard(
        enabled = showSongKb,
        spec = DashKeyboardSpec(
            id = "capsule_deck_search",
            preview = songQuery,
            voiceStatus = voiceStatus,
            placeholder = "Play any song, artist, or mix in $appBadge…",
            enterLabel = "PLAY ↵",
            onText = { songQuery = (songQuery + it).take(64) },
            onBackspace = { songQuery = songQuery.dropLast(1) },
            onClear = { songQuery = "" },
            onEnter = {
                if (songQuery.isNotBlank()) MediaBrowserHub.wakeAndPlay(ctx, activePkg, songQuery.trim())
                showSongKb = false
            },
            onVoice = {
                VoiceSearch.listen(ctx, { voiceStatus = it }, { spoken ->
                    if (!spoken.isNullOrBlank()) {
                        MediaBrowserHub.wakeAndPlay(ctx, activePkg, spoken.trim())
                        voiceStatus = null
                        showSongKb = false
                    }
                })
            },
            onClose = { showSongKb = false }
        )
    )
    val stadium = RoundedCornerShape(999.dp)
    if (!isNativeAudio) {
        Box(
            modifier
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.horizontalGradient(listOf(p.glassA, p.glassB)))
                .border(1.4.dp, p.acc.copy(alpha = 0.55f), RoundedCornerShape(30.dp))
                .padding(7.dp)
        ) {
            fallbackMedia(Modifier.fillMaxSize(), true)
        }
    } else {
        BoxWithConstraints(
            modifier
                .shadow(8.dp, stadium)
                .clip(stadium)
                .background(Brush.horizontalGradient(listOf(p.panel2, p.panel, p.panel2)))
                .border(1.4.dp, p.acc.copy(alpha = 0.55f), stadium)
                .padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            val puck = (maxHeight - 2.dp).coerceIn(48.dp, 98.dp)
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier
                        .size(puck)
                        .clip(CircleShape)
                        .background(p.accDim)
                        .border(2.dp, if (playing) p.acc else p.hairline, CircleShape)
                        .clickable { onOpenMediaPicker() },
                    contentAlignment = Alignment.Center
                ) {
                    val art = np?.art
                    if (art != null) Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else if (appIcon != null) Image(appIcon, null, Modifier.size(puck * 0.62f).clip(CircleShape))
                    else Icon(Icons.Rounded.MusicNote, null, Modifier.size(puck * 0.45f), tint = p.acc)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("✓ ${appBadge.uppercase()}", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onOpenMediaPicker() })
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (audioArmed) p.accDim else p.panel2)
                                .border(1.dp, if (audioArmed) p.acc else p.hairline, RoundedCornerShape(99.dp))
                                .clickable { if (audioArmed) AaAudioStreamer.stopAll(ctx) else AudioCaptureGrantActivity.launch(ctx) }
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(if (audioStreaming) "🔊 LIVE" else if (audioArmed) "🔊 CAR" else "🔊 BT", color = if (audioArmed) p.acc else p.soft, fontFamily = FontFamily.Monospace, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(p.panel2)
                                .border(1.dp, p.hairline, RoundedCornerShape(99.dp))
                                .clickable { showSongKb = !showSongKb }
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) { Text("🔎 SONG", color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 8.5.sp, fontWeight = FontWeight.Bold) }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(p.accDim)
                                .border(1.dp, p.acc.copy(alpha = 0.6f), RoundedCornerShape(99.dp))
                                .clickable {
                                    VoiceSearch.listen(ctx, { voiceStatus = it }, { spoken ->
                                        if (!spoken.isNullOrBlank()) {
                                            MediaBrowserHub.wakeAndPlay(ctx, activePkg, spoken.trim())
                                            voiceStatus = null
                                        }
                                    })
                                }
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) { Text("🎤", fontSize = 9.5.sp) }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(p.panel2)
                                .border(1.dp, p.hairline, RoundedCornerShape(99.dp))
                                .clickable { onExpand() }
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) { Text("⤢", color = p.ink, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                    }
                    Text(title, color = p.ink, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(voiceStatus ?: artist, color = if (voiceStatus != null) p.acc else p.soft, fontSize = 10.5.sp, maxLines = 1)
                        listOf("Liked Songs", "Driving Mix", "Chill Mix").forEach { mix ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(p.panel)
                                    .border(1.dp, p.hairline, RoundedCornerShape(99.dp))
                                    .clickable { MediaBrowserHub.wakeAndPlay(ctx, activePkg, mix) }
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text("▶ $mix", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(p.panel)
                            .border(1.dp, p.hairline, CircleShape)
                            .clickable {
                                if (!MediaSessionBridge.prev(embeddedMediaApp?.packageName)) {
                                    if (embeddedMediaApp != null) MediaBrowserHub.sendTargetedMediaButton(ctx, embeddedMediaApp.packageName, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                                    else dispatchMediaFallbackKey(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.SkipPrevious, "Prev", Modifier.size(18.dp), tint = p.ink) }
                    Box(
                        Modifier
                            .size(puck)
                            .clip(CircleShape)
                            .background(p.accentBrush)
                            .clickable {
                                if (embeddedMediaApp != null) {
                                    if (!MediaSessionBridge.playPausePackage(embeddedMediaApp.packageName)) MediaBrowserHub.wakeAndPlay(ctx, embeddedMediaApp.packageName)
                                } else if (!MediaSessionBridge.playPause()) {
                                    dispatchMediaFallbackKey(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (playing) "❚❚" else "▶", color = if (p.isLight) Color.White else Color(0xFF05070A), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(p.panel)
                            .border(1.dp, p.hairline, CircleShape)
                            .clickable {
                                if (!MediaSessionBridge.next(embeddedMediaApp?.packageName)) {
                                    if (embeddedMediaApp != null) MediaBrowserHub.sendTargetedMediaButton(ctx, embeddedMediaApp.packageName, KeyEvent.KEYCODE_MEDIA_NEXT)
                                    else dispatchMediaFallbackKey(ctx, KeyEvent.KEYCODE_MEDIA_NEXT)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(18.dp), tint = p.ink) }
                }
            }
        }
    }
}
@Composable
fun BespokeLayoutHost(
    layout: TileLayout,
    pad: Dp,
    gap: Dp,
    dense: Boolean,
    swapMapMedia: Boolean,
    location: Location?,
    speedMph: Int,
    route: RouteResult?,
    weather: WeatherNow?,
    cfg: DriveConfig,
    hasAmniMap: Boolean,
    useAmniMap: Boolean,
    carSurface: Boolean,
    embeddedMediaApp: AppEntry?,
    embeddedWebApp: WebAppEntry?,
    mediaHint: String?,
    allApps: List<AppEntry>,
    onQuickLaunch: (AppEntry) -> Unit,
    onOpenAllApps: () -> Unit,
    onOpenOrbSearch: () -> Unit,
    onVoice: (() -> Unit)?,
    onOpenMapStudio: () -> Unit,
    onToggleAmniMap: () -> Unit,
    onOpenMediaPicker: () -> Unit,
    onClearMediaApp: () -> Unit,
    onToggleExpand: (String) -> Unit,
    map: @Composable (Modifier, Boolean) -> Unit,
    media: @Composable (Modifier, Boolean) -> Unit,
    widgets: @Composable (Modifier) -> Unit
) {
    val p = LocalAmni.current
    val mapDial: @Composable (Modifier) -> Unit = { mod ->
        BespokeCircularMapPod(
            modifier = mod,
            location = location,
            speedMph = speedMph,
            route = route,
            hasAmniMap = hasAmniMap,
            useAmniMap = useAmniMap,
            carSurface = carSurface,
            onOpenSearch = onOpenOrbSearch,
            onVoice = onVoice,
            onOpenStudio = onOpenMapStudio,
            onToggleEngine = onToggleAmniMap,
            onExpand = { onToggleExpand("MAP") },
            mapContent = map
        )
    }
    val mediaDial: @Composable (Modifier) -> Unit = { mod ->
        BespokeCircularMediaPod(
            modifier = mod,
            embeddedMediaApp = embeddedMediaApp,
            embeddedWebApp = embeddedWebApp,
            mediaHint = mediaHint,
            onOpenMediaPicker = onOpenMediaPicker,
            onClearMediaApp = onClearMediaApp,
            onExpand = { onToggleExpand("MEDIA") },
            portholeFallback = media
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(pad)) {
        val rootW = maxWidth
        val rootH = maxHeight
        val portrait = rootH > rootW * 1.05f
        when (layout) {
            TileLayout.DRUM_3D -> {
                com.amni.drive.helium.HeliumDrumCockpit(
                    theme = UiTheme(preset = AmniPreset.entries.firstOrNull { it.light == p.isLight } ?: AmniPreset.AMNI_DARK),
                    speedMph = speedMph,
                    weather = weather,
                    mapContent = { m -> map(m, false) },
                    mediaContent = { m -> media(m, dense) },
                    appsContent = { m -> CircularRotaryAppsPod(allApps, onQuickLaunch, onOpenAllApps, m) },
                    onOpenSettings = onOpenMapStudio,
                    modifier = Modifier.fillMaxSize()
                )
            }
            TileLayout.TWIN_CHRONO -> {
                if (portrait) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (!swapMapMedia) mapDial(Modifier.weight(1.1f).fillMaxWidth()) else mediaDial(Modifier.weight(1.1f).fillMaxWidth())
                        Row(Modifier.height(94.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            CircularSpeedChrono(speedMph, route, Modifier.size(90.dp))
                            CircularWeatherDial(weather, Modifier.size(90.dp))
                            CircularRotaryAppsPod(allApps, onQuickLaunch, onOpenAllApps, Modifier.size(90.dp))
                        }
                        if (!swapMapMedia) mediaDial(Modifier.weight(1.05f).fillMaxWidth()) else mapDial(Modifier.weight(1.05f).fillMaxWidth())
                    }
                } else {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
                        if (!swapMapMedia) mapDial(Modifier.weight(1f).fillMaxHeight()) else mediaDial(Modifier.weight(1f).fillMaxHeight())
                        Column(
                            Modifier.width((rootH * 0.34f).coerceIn(92.dp, 148.dp)).fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularSpeedChrono(speedMph, route, Modifier.weight(1f).fillMaxWidth())
                            Spacer(Modifier.height(5.dp))
                            CircularWeatherDial(weather, Modifier.weight(1f).fillMaxWidth())
                        }
                        if (!swapMapMedia) mediaDial(Modifier.weight(1f).fillMaxHeight()) else mapDial(Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            TileLayout.SOLAR_ORBIT -> {
                if (portrait) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
                        mapDial(Modifier.weight(1.35f).fillMaxWidth())
                        Row(Modifier.weight(0.95f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            Column(Modifier.weight(0.85f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                                CircularSpeedChrono(speedMph, route, Modifier.weight(1f).fillMaxWidth())
                                CircularWeatherDial(weather, Modifier.weight(1f).fillMaxWidth())
                            }
                            mediaDial(Modifier.weight(1.15f).fillMaxHeight())
                        }
                    }
                } else {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
                        Column(Modifier.weight(0.62f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularSpeedChrono(speedMph, route, Modifier.weight(1f).fillMaxWidth())
                            CircularWeatherDial(weather, Modifier.weight(1f).fillMaxWidth())
                        }
                        mapDial(Modifier.weight(1.28f).fillMaxHeight())
                        Column(Modifier.weight(0.72f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
                            mediaDial(Modifier.weight(1.18f).fillMaxWidth())
                            CircularRotaryAppsPod(allApps, onQuickLaunch, onOpenAllApps, Modifier.weight(0.82f).fillMaxWidth())
                        }
                    }
                }
            }
            TileLayout.ORB_CENTER -> {
                if (portrait) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
                        mapDial(Modifier.weight(1.25f).fillMaxWidth())
                        CapsuleMediaDeck(Modifier.height(104.dp).fillMaxWidth(), embeddedMediaApp, embeddedWebApp, mediaHint, onOpenMediaPicker, { onToggleExpand("MEDIA") }, media)
                        Row(Modifier.height(92.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            CircularSpeedChrono(speedMph, route, Modifier.size(88.dp))
                            CircularWeatherDial(weather, Modifier.size(88.dp))
                            CircularRotaryAppsPod(allApps, onQuickLaunch, onOpenAllApps, Modifier.size(88.dp))
                        }
                    }
                } else {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
                        Column(Modifier.weight(0.58f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularSpeedChrono(speedMph, route, Modifier.weight(1f).fillMaxWidth())
                            CircularWeatherDial(weather, Modifier.weight(1f).fillMaxWidth())
                        }
                        mapDial(Modifier.weight(1.15f).fillMaxHeight())
                        Box(
                            Modifier
                                .weight(0.95f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(34.dp))
                                .border(1.2.dp, p.acc.copy(alpha = 0.45f), RoundedCornerShape(34.dp))
                        ) {
                            media(Modifier.fillMaxSize(), dense)
                        }
                    }
                }
            }
            TileLayout.HORIZON_DIALS -> {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).border(1.dp, p.hairline, RoundedCornerShape(24.dp))) {
                        map(Modifier.fillMaxSize(), false)
                    }
                    val orbSz = (minOf(rootW, rootH) * 0.46f).coerceIn(138.dp, 224.dp)
                    val compSz = (orbSz * 0.56f).coerceIn(84.dp, 118.dp)
                    Row(
                        Modifier.align(Alignment.BottomStart).padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        CircularSpeedChrono(speedMph, route, Modifier.size(compSz))
                        CircularWeatherDial(weather, Modifier.size(compSz))
                    }
                    Box(Modifier.align(Alignment.BottomEnd).padding(10.dp).size(orbSz)) {
                        mediaDial(Modifier.fillMaxSize())
                    }
                }
            }
            TileLayout.HEX_COCKPIT -> {
                if (portrait) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                        ChamferPod(Modifier.weight(1.25f).fillMaxWidth(), cutDp = 24.dp, accentGlow = true) {
                            map(Modifier.fillMaxSize().padding(3.dp), false)
                        }
                        ChamferPod(Modifier.weight(0.95f).fillMaxWidth(), cutDp = 20.dp) {
                            media(Modifier.fillMaxSize().padding(4.dp), dense)
                        }
                    }
                } else {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                        val mapPod: @Composable (Modifier) -> Unit = { m ->
                            ChamferPod(m, cutDp = 26.dp, accentGlow = true) { map(Modifier.fillMaxSize().padding(3.dp), false) }
                        }
                        val medPod: @Composable (Modifier) -> Unit = { m ->
                            ChamferPod(m, cutDp = 22.dp) { media(Modifier.fillMaxSize().padding(4.dp), dense) }
                        }
                        if (!swapMapMedia) {
                            mapPod(Modifier.weight(1.32f).fillMaxHeight())
                            medPod(Modifier.weight(0.98f).fillMaxHeight())
                        } else {
                            medPod(Modifier.weight(0.98f).fillMaxHeight())
                            mapPod(Modifier.weight(1.32f).fillMaxHeight())
                        }
                        ChamferPod(Modifier.weight(0.56f).fillMaxHeight(), cutDp = 18.dp) {
                            widgets(Modifier.fillMaxSize().padding(9.dp))
                        }
                    }
                }
            }
            TileLayout.CAPSULE_DECK -> {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    val mapCapsule = RoundedCornerShape(42.dp)
                    Box(
                        Modifier
                            .weight(1.42f)
                            .fillMaxWidth()
                            .shadow(8.dp, mapCapsule)
                            .clip(mapCapsule)
                            .border(1.5.dp, p.acc.copy(alpha = 0.55f), mapCapsule)
                    ) {
                        map(Modifier.fillMaxSize(), false)
                    }
                    Row(
                        Modifier.weight(0.72f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CapsuleMediaDeck(
                            Modifier.weight(1.65f).fillMaxHeight(),
                            embeddedMediaApp = embeddedMediaApp,
                            embeddedWebApp = embeddedWebApp,
                            mediaHint = mediaHint,
                            onOpenMediaPicker = onOpenMediaPicker,
                            onExpand = { onToggleExpand("MEDIA") },
                            fallbackMedia = media
                        )
                        if (!portrait) {
                            CircularSpeedChrono(speedMph, route, Modifier.fillMaxHeight().width((rootH * 0.28f).coerceIn(84.dp, 132.dp)))
                            CircularWeatherDial(weather, Modifier.fillMaxHeight().width((rootH * 0.28f).coerceIn(84.dp, 132.dp)))
                        }
                    }
                }
            }
            else -> {
                val leftSculpt = RoundedCornerShape(topStart = 34.dp, bottomStart = 34.dp, topEnd = 12.dp, bottomEnd = 12.dp)
                val midSculpt = RoundedCornerShape(14.dp)
                if (portrait) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                        Box(Modifier.weight(1.3f).fillMaxWidth().clip(RoundedCornerShape(30.dp)).border(1.4.dp, p.acc.copy(alpha = 0.5f), RoundedCornerShape(30.dp))) {
                            map(Modifier.fillMaxSize(), false)
                        }
                        Row(Modifier.weight(0.95f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            Box(Modifier.weight(1.3f).fillMaxHeight().clip(RoundedCornerShape(26.dp)).border(1.dp, p.hairline, RoundedCornerShape(26.dp))) {
                                media(Modifier.fillMaxSize(), dense)
                            }
                            Column(Modifier.weight(0.7f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularSpeedChrono(speedMph, route, Modifier.weight(1f).fillMaxWidth())
                                CircularWeatherDial(weather, Modifier.weight(1f).fillMaxWidth())
                            }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1.618f).fillMaxHeight().clip(leftSculpt).border(1.4.dp, p.acc.copy(alpha = 0.5f), leftSculpt)) {
                            if (!swapMapMedia) map(Modifier.fillMaxSize(), false) else media(Modifier.fillMaxSize(), dense)
                        }
                        Box(Modifier.weight(1.0f).fillMaxHeight().clip(midSculpt).border(1.dp, p.hairline, midSculpt)) {
                            if (!swapMapMedia) media(Modifier.fillMaxSize(), dense) else map(Modifier.fillMaxSize(), false)
                        }
                        Column(
                            Modifier.weight(0.56f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(gap),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularSpeedChrono(speedMph, route, Modifier.weight(1f).fillMaxWidth())
                            CircularWeatherDial(weather, Modifier.weight(1f).fillMaxWidth())
                            CircularRotaryAppsPod(allApps, onQuickLaunch, onOpenAllApps, Modifier.weight(1f).fillMaxWidth())
                        }
                    }
                }
            }
        }
        cfg.toString()
    }
}
@Composable
fun InAppUpdateBanner(
    ota: OtaUpdater.OtaState,
    onInstallNow: () -> Unit,
    onOpenDetails: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalAmni.current
    val busy = ota.downloadingId != null
    val pct = (ota.downloadProgress * 100f).roundToInt().coerceIn(1, 99)
    val headline = when {
        busy -> "⬇ Updating ${ota.downloadingId} · $pct%"
        ota.pendingInstallApkPath != null -> "✓ Update verified — tap INSTALL to finish"
        ota.selfUpdateAvailable && ota.selfRelease != null -> "⬆ Amni-Drive v${ota.selfRelease.versionName} update ready"
        ota.bundledMapMissing && ota.bundledMap != null -> "⚡ Amni-Map v${ota.bundledMap.versionName} is prebuilt inside — install in 1 tap"
        ota.bundledMapUpdateReady && ota.bundledMap != null -> "⚡ Built-in Amni-Map v${ota.bundledMap.versionName} update included"
        else -> "⬆ ${ota.totalUpdatesCount} Amni app update${if (ota.totalUpdatesCount > 1) "s" else ""} ready"
    }
    val ctaLabel = when {
        busy -> "$pct%"
        ota.pendingInstallApkPath != null -> "FINISH INSTALL"
        ota.bundledMapMissing -> "INSTALL BUILT-IN"
        else -> "UPDATE NOW"
    }
    Row(
        modifier
            .shadow(10.dp, RoundedCornerShape(99.dp))
            .clip(RoundedCornerShape(99.dp))
            .background(p.panel.copy(alpha = 0.98f))
            .border(1.5.dp, p.acc, RoundedCornerShape(99.dp))
            .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            headline,
            color = p.ink,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 360.dp).clickable { onOpenDetails() }
        )
        Box(
            Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(p.acc)
                .clickable(enabled = !busy) { onInstallNow() }
                .padding(horizontal = 11.dp, vertical = 5.dp)
        ) {
            Text(
                ctaLabel,
                color = if (p.isLight) Color.White else Color(0xFF05070A),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(p.panel2)
                .border(1.dp, p.hairline, RoundedCornerShape(99.dp))
                .clickable { onOpenDetails() }
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Text("NOTES", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "✕",
            color = p.dim,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onDismiss() }.padding(horizontal = 5.dp)
        )
    }
}
@Composable
fun InAppUpdatePromptDialog(
    ota: OtaUpdater.OtaState,
    onInstallNow: () -> Unit,
    onOpenDetails: () -> Unit,
    onLater: () -> Unit
) {
    val p = LocalAmni.current
    val busy = ota.downloadingId != null
    val pct = (ota.downloadProgress * 100f).roundToInt().coerceIn(1, 99)
    val title = when {
        ota.pendingInstallApkPath != null -> "Update Verified & Ready"
        ota.selfUpdateAvailable && ota.selfRelease != null -> "Amni-Drive v${ota.selfRelease.versionName} Available"
        ota.bundledMapMissing && ota.bundledMap != null -> "Install Built-In Amni-Map v${ota.bundledMap.versionName}"
        ota.bundledMapUpdateReady && ota.bundledMap != null -> "Built-In Amni-Map v${ota.bundledMap.versionName} Ready"
        else -> "${ota.totalUpdatesCount} Amni App Update${if (ota.totalUpdatesCount > 1) "s" else ""} Available"
    }
    val body = when {
        ota.selfUpdateAvailable && ota.selfRelease != null -> ota.selfRelease.notes.ifBlank { "A new signed Amni-Drive release is ready to download and install." }
        (ota.bundledMapMissing || ota.bundledMapUpdateReady) && ota.bundledMap != null -> "Amni-Map v${ota.bundledMap.versionName} is prebuilt right inside Amni-Drive for instant offline installation."
        else -> "Signed updates are available for your installed Amni-Scient apps."
    }
    val cta = when {
        busy -> "DOWNLOADING $pct%"
        ota.pendingInstallApkPath != null -> "FINISH INSTALL"
        ota.bundledMapMissing || ota.bundledMapUpdateReady -> "INSTALL BUILT-IN"
        else -> "DOWNLOAD & UPDATE"
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!busy) onLater() },
        containerColor = p.panel,
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(title, color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(body, color = p.soft, fontSize = 13.sp, lineHeight = 18.sp)
                if (busy) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { ota.downloadProgress.coerceIn(0.02f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(99.dp)),
                        color = p.acc,
                        trackColor = p.panel2
                    )
                    Text("Downloading & verifying SHA-256 · $pct%", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                ota.errorMsg?.takeIf { it.isNotBlank() }?.let { err ->
                    Text(err, color = p.gold, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Box(
                Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (busy) p.acc.copy(alpha = 0.55f) else p.acc)
                    .clickable(enabled = !busy) { onInstallNow() }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(cta, color = if (p.isLight) Color.White else Color(0xFF05070A), fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            if (!busy) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(p.panel2)
                            .border(1.dp, p.hairline, RoundedCornerShape(99.dp))
                            .clickable { onOpenDetails() }
                            .padding(horizontal = 11.dp, vertical = 9.dp)
                    ) {
                        Text("ALL APPS", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .clickable { onLater() }
                            .padding(horizontal = 11.dp, vertical = 9.dp)
                    ) {
                        Text("LATER", color = p.dim, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    )
}
