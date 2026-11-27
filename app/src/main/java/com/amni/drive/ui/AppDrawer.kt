package com.amni.drive.ui
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.amni.drive.config.DriveConfig
import com.amni.drive.display.DisplayRole
import com.amni.drive.gate.AppCategory
import com.amni.drive.gate.DriveState
import com.amni.drive.gate.GateManager
import com.amni.drive.gate.GateResolver
import com.amni.drive.launcher.AppEntry
import com.amni.drive.media.MediaBrowserHub
import com.amni.drive.voice.VoiceSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
private val FILTERS = listOf("ALL" to null, "NAV" to AppCategory.NAV, "MEDIA" to AppCategory.MEDIA, "VIDEO" to AppCategory.VIDEO, "GAMES" to AppCategory.GAME, "COMMS" to AppCategory.COMMS, "OTHER" to AppCategory.SAFE)
@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    cfg: DriveConfig,
    state: DriveState,
    passenger: Boolean,
    tileScale: Int,
    appShape: AppLauncherShape = AppLauncherShape.AUTO,
    activeLayout: TileLayout = TileLayout.AUTO,
    onSetAppShape: (AppLauncherShape) -> Unit = {},
    onLaunch: (AppEntry) -> Unit
) {
    val p = LocalAmni.current
    val ctx = LocalContext.current
    var filter by remember { mutableStateOf<AppCategory?>(null) }
    var query by remember { mutableStateOf("") }
    var showKb by remember { mutableStateOf(false) }
    var voiceStatus by remember { mutableStateOf<String?>(null) }
    val effShape = appShape.resolve(activeLayout)
    val catShown = if (filter == null) apps else apps.filter { it.category == filter || (filter == AppCategory.SAFE && it.category == AppCategory.INTERACTIVE) }
    val shown = if (query.isBlank()) catShown else catShown.filter { it.label.contains(query.trim(), ignoreCase = true) || it.packageName.contains(query.trim(), ignoreCase = true) }
    val cell = when (tileScale) { 0 -> 97.dp; 2 -> 137.dp; else -> 114.dp }
    val startVoiceSearch: () -> Unit = {
        VoiceSearch.listen(ctx, { voiceStatus = it }, { spoken ->
            if (!spoken.isNullOrBlank()) {
                val clean = spoken.trim()
                val exact = apps.firstOrNull { it.label.equals(clean, true) } ?: apps.firstOrNull { it.label.contains(clean, true) }
                if (exact != null) {
                    voiceStatus = null
                    showKb = false
                    onLaunch(exact)
                } else {
                    query = clean
                    voiceStatus = null
                    showKb = false
                }
            }
        })
    }
    val dockedKb = BindDashKeyboard(
        enabled = showKb,
        spec = DashKeyboardSpec(
            id = "app_drawer_filter",
            preview = query,
            voiceStatus = voiceStatus,
            placeholder = "Filter or launch installed apps…",
            enterLabel = if (shown.size == 1) "OPEN ↵" else "DONE ↵",
            onText = { query = (query + it).take(32) },
            onBackspace = { query = query.dropLast(1) },
            onClear = { query = "" },
            onEnter = {
                if (shown.size == 1) {
                    val pick = shown.first()
                    showKb = false
                    onLaunch(pick)
                } else showKb = false
            },
            onVoice = startVoiceSearch,
            onClose = { showKb = false }
        )
    )
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(99.dp))
                        .background(if (showKb || query.isNotBlank()) p.acc else p.panel2)
                        .border(1.dp, p.acc.copy(alpha = 0.65f), RoundedCornerShape(99.dp))
                        .clickable { showKb = !showKb }
                        .padding(horizontal = 11.dp, vertical = 7.dp)
                ) {
                    Text(
                        if (query.isBlank()) "🔎 SEARCH" else "🔎 ${query.take(12)}",
                        color = if (showKb || query.isNotBlank()) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.acc,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (query.isNotBlank()) {
                    Box(
                        Modifier.clip(RoundedCornerShape(99.dp)).background(p.panel2).border(1.dp, p.hairline, RoundedCornerShape(99.dp))
                            .clickable { query = ""; showKb = false }.padding(horizontal = 9.dp, vertical = 7.dp)
                    ) {
                        Text("✕", color = p.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FILTERS.forEach { (label, cat) ->
                        val sel = filter == cat
                        Box(
                            Modifier.clip(RoundedCornerShape(99.dp))
                                .background(if (sel) Brush.horizontalGradient(listOf(p.acc.copy(alpha = 0.24f), p.acc.copy(alpha = 0.10f))) else Brush.horizontalGradient(listOf(p.panel.copy(alpha = 0.8f), p.panel.copy(alpha = 0.8f))))
                                .border(1.dp, if (sel) p.acc.copy(alpha = 0.6f) else p.hairline, RoundedCornerShape(99.dp))
                                .clickable { filter = cat }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(label, color = if (sel) p.acc else p.soft, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppLauncherShape.entries.forEach { sh ->
                        val sel = appShape == sh
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(if (sel) p.acc else p.panel2)
                                .border(1.dp, if (sel) p.acc else p.hairline, CircleShape)
                                .clickable { onSetAppShape(sh) }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                sh.glyph,
                                color = if (sel) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            when (effShape) {
                AppLauncherShape.ORBITAL -> OrbitalRotaryLauncher(shown, cfg, state, passenger, cell, startVoiceSearch) { showKb = false; onLaunch(it) }
                AppLauncherShape.MEDALLION -> LazyVerticalGrid(GridCells.Adaptive(cell), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(shown, key = { it.packageName }) { app ->
                        MedallionAppOrb(app, isGated(app, cfg, state, passenger), MediaBrowserHub.isAudioApp(ctx, app.packageName), cell) { showKb = false; onLaunch(app) }
                    }
                }
                AppLauncherShape.HONEYCOMB -> HoneycombHexGrid(shown, cfg, state, passenger, cell) { showKb = false; onLaunch(it) }
                AppLauncherShape.CAPSULE -> LazyVerticalGrid(GridCells.Adaptive((cell * 2.15f).coerceAtLeast(210.dp)), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it.packageName }) { app ->
                        StadiumCapsuleAppRow(app, isGated(app, cfg, state, passenger), MediaBrowserHub.isAudioApp(ctx, app.packageName)) { showKb = false; onLaunch(app) }
                    }
                }
                else -> LazyVerticalGrid(GridCells.Adaptive(cell), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(shown, key = { it.packageName }) { app -> AppCell(app, isGated(app, cfg, state, passenger), cell) { showKb = false; onLaunch(app) } }
                }
            }
        }
        if (showKb && !dockedKb) {
            Box(Modifier.fillMaxSize().clickable { showKb = false }, contentAlignment = Alignment.BottomCenter) {
                CarTouchKeyboard(
                    preview = query,
                    voiceStatus = voiceStatus,
                    placeholder = "Filter installed apps…",
                    enterLabel = if (shown.size == 1) "OPEN ↵" else "DONE ↵",
                    onText = { query = (query + it).take(32) },
                    onBackspace = { query = query.dropLast(1) },
                    onClear = { query = "" },
                    onEnter = {
                        if (shown.size == 1) {
                            val pick = shown.first()
                            showKb = false
                            onLaunch(pick)
                        } else showKb = false
                    },
                    onVoice = startVoiceSearch,
                    onClose = { showKb = false }
                )
            }
        }
    }
}
@Composable
private fun OrbitalRotaryLauncher(
    shown: List<AppEntry>,
    cfg: DriveConfig,
    state: DriveState,
    passenger: Boolean,
    cell: Dp,
    onVoiceLaunch: () -> Unit,
    onLaunch: (AppEntry) -> Unit
) {
    val p = LocalAmni.current
    val ctx = LocalContext.current
    val density = LocalDensity.current
    var spinDeg by remember { mutableFloatStateOf(0f) }
    val ringApps = remember(shown) { shown.take(8) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val portrait = maxHeight > maxWidth * 1.05f
        val wheelDp = (if (portrait) minOf(maxWidth * 0.88f, maxHeight * 0.50f) else minOf(maxHeight * 0.94f, maxWidth * 0.48f)).coerceAtLeast(190.dp)
        val wheelComposable: @Composable () -> Unit = {
            val orbitPx = with(density) { (wheelDp * 0.34f).toPx() }
            val puckDp = (wheelDp * 0.22f).coerceIn(44.dp, 68.dp)
            Box(
                Modifier
                    .size(wheelDp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            spinDeg = (spinDeg + (drag.x - drag.y) * 0.35f) % 360f
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension * 0.5f
                    drawCircle(brush = Brush.radialGradient(listOf(p.panel2.copy(alpha = 0.7f), p.bg.copy(alpha = 0.2f))), radius = r)
                    drawCircle(color = p.acc.copy(alpha = 0.45f), radius = r - 3.dp.toPx(), style = Stroke(width = 1.6.dp.toPx()))
                    drawCircle(color = p.hairline, radius = orbitPx, style = Stroke(width = 1.2.dp.toPx()))
                }
                val count = ringApps.size.coerceAtLeast(1)
                ringApps.forEachIndexed { idx, app ->
                    val deg = (idx * (360f / count)) + spinDeg - 90f
                    val rad = deg * (PI.toFloat() / 180f)
                    val ox = (orbitPx * cos(rad)).roundToInt()
                    val oy = (orbitPx * sin(rad)).roundToInt()
                    val icon = rememberAppIcon(app.packageName)
                    val gated = isGated(app, cfg, state, passenger)
                    Column(
                        Modifier.offset { IntOffset(ox, oy) }.widthIn(max = puckDp + 18.dp).clickable { onLaunch(app) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier
                                .size(puckDp)
                                .shadow(7.dp, CircleShape)
                                .clip(CircleShape)
                                .background(p.panel)
                                .border(1.5.dp, if (gated) p.err.copy(alpha = 0.7f) else p.acc.copy(alpha = 0.75f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (icon != null) Image(icon, app.label, Modifier.size(puckDp * 0.70f).clip(CircleShape))
                            else Text(app.label.take(1).uppercase(), color = p.acc, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(app.label, color = p.ink, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Column(
                    Modifier
                        .size((wheelDp * 0.28f).coerceAtLeast(58.dp))
                        .shadow(8.dp, CircleShape)
                        .clip(CircleShape)
                        .background(p.panel)
                        .border(1.8.dp, p.acc, CircleShape)
                        .clickable { onVoiceLaunch() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("🎤", fontSize = 16.sp)
                    Text("SPIN / SAY", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (portrait) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                wheelComposable()
                LazyVerticalGrid(GridCells.Adaptive(cell), Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(shown, key = { it.packageName }) { app ->
                        MedallionAppOrb(app, isGated(app, cfg, state, passenger), MediaBrowserHub.isAudioApp(ctx, app.packageName), cell) { onLaunch(app) }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                wheelComposable()
                LazyVerticalGrid(GridCells.Adaptive(cell), Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(shown, key = { it.packageName }) { app ->
                        MedallionAppOrb(app, isGated(app, cfg, state, passenger), MediaBrowserHub.isAudioApp(ctx, app.packageName), cell) { onLaunch(app) }
                    }
                }
            }
        }
    }
}
@Composable
private fun MedallionAppOrb(app: AppEntry, gated: Boolean, signedAudio: Boolean, cell: Dp, onClick: () -> Unit) {
    val p = LocalAmni.current
    val icon = rememberAppIcon(app.packageName)
    val orbDp = (cell - 22.dp).coerceAtLeast(64.dp)
    val rimTint = when {
        gated -> p.err.copy(alpha = 0.7f)
        signedAudio || app.category == AppCategory.MEDIA -> p.acc
        app.category == AppCategory.NAV -> p.gold
        else -> p.line2
    }
    Column(
        Modifier.clickable { onClick() }.padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(orbDp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxSize()
                    .shadow(7.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(p.panel2, p.panel)))
                    .border(1.8.dp, rimTint, CircleShape)
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) Image(icon, app.label, Modifier.fillMaxSize().clip(CircleShape))
                else Text(app.label.take(1).uppercase(), color = p.ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
            if (gated) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(p.bg).border(1.dp, p.err, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Lock, "gated", Modifier.size(12.dp), tint = p.err) }
            } else if (signedAudio) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(20.dp).clip(CircleShape).background(p.acc),
                    contentAlignment = Alignment.Center
                ) { Text("✓", color = if (p.isLight) Color.White else Color(0xFF05070A), fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Text(app.label, color = p.ink, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}
@Composable
private fun HoneycombHexGrid(
    shown: List<AppEntry>,
    cfg: DriveConfig,
    state: DriveState,
    passenger: Boolean,
    cell: Dp,
    onLaunch: (AppEntry) -> Unit
) {
    val p = LocalAmni.current
    val density = LocalDensity.current
    val strokePx = with(density) { 1.5.dp.toPx() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val hexW = cell.coerceIn(88.dp, 128.dp)
        val hexH = hexW * 1.08f
        val cols = ((maxWidth - hexW * 0.5f) / (hexW + 8.dp)).toInt().coerceAtLeast(2)
        val rows = shown.chunked(cols)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            rows.forEachIndexed { rIdx, rowApps ->
                Row(
                    Modifier.padding(start = if (rIdx % 2 == 1) (hexW + 8.dp) * 0.5f else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowApps.forEach { app ->
                        val gated = isGated(app, cfg, state, passenger)
                        val icon = rememberAppIcon(app.packageName)
                        Box(
                            Modifier
                                .size(hexW, hexH)
                                .clip(HexagonCellShape)
                                .background(Brush.verticalGradient(listOf(p.panel2, p.panel)), HexagonCellShape)
                                .clickable { onLaunch(app) },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawPath(
                                    buildHexagonPath(size),
                                    color = if (gated) p.err.copy(alpha = 0.7f) else p.acc.copy(alpha = 0.65f),
                                    style = Stroke(width = strokePx)
                                )
                            }
                            Column(
                                Modifier.padding(horizontal = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (icon != null) Image(icon, app.label, Modifier.size(hexW * 0.44f).clip(CircleShape))
                                else Text(app.label.take(1).uppercase(), color = p.acc, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(app.label, color = p.ink, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun StadiumCapsuleAppRow(app: AppEntry, gated: Boolean, signedAudio: Boolean, onClick: () -> Unit) {
    val p = LocalAmni.current
    val icon = rememberAppIcon(app.packageName)
    val stadium = RoundedCornerShape(999.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .shadow(5.dp, stadium)
            .clip(stadium)
            .background(Brush.horizontalGradient(listOf(p.panel2, p.panel)), stadium)
            .border(1.2.dp, if (gated) p.err.copy(alpha = 0.65f) else if (signedAudio) p.acc.copy(alpha = 0.65f) else p.hairline, stadium)
            .clickable { onClick() }
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(p.panel).border(1.dp, p.hairline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) Image(icon, app.label, Modifier.size(34.dp).clip(CircleShape))
            else Text(app.label.take(1).uppercase(), color = p.acc, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(app.label, color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    gated -> "Gated while moving · rear unlocked"
                    signedAudio -> "✓ Signed in on phone · Audio"
                    else -> app.category.name.lowercase().replaceFirstChar { it.uppercase() }
                },
                color = if (signedAudio) p.acc else p.soft,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(if (gated) p.err.copy(alpha = 0.16f) else p.accDim),
            contentAlignment = Alignment.Center
        ) {
            if (gated) Icon(Icons.Rounded.Lock, "gated", Modifier.size(14.dp), tint = p.err)
            else Text("→", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
private fun isGated(app: AppEntry, cfg: DriveConfig, state: DriveState, passenger: Boolean): Boolean =
    !GateManager.decide(GateResolver.input(app.category, DisplayRole.PRIMARY_DRIVER, state, cfg, passenger)).allow
@Composable
private fun AppCell(app: AppEntry, gated: Boolean, cell: Dp, onClick: () -> Unit) {
    val p = LocalAmni.current
    val icon = rememberAppIcon(app.packageName)
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.shadow(7.dp, shape, ambientColor = p.acc.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.35f))
            .clip(shape)
            .background(p.surfaceBrush, shape)
            .border(1.dp, p.hairline, shape)
            .clickable { onClick() }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box {
            Box(
                Modifier.size(cell - 38.dp).clip(RoundedCornerShape(18.dp)).background(p.panel2).border(1.dp, p.hairline, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) Image(icon, app.label, Modifier.size(cell - 52.dp).clip(RoundedCornerShape(14.dp)))
                else Text(app.label.take(1).uppercase(), color = p.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            if (gated) Box(
                Modifier.align(Alignment.TopEnd).size(26.dp).clip(CircleShape)
                    .background(if (p.isLight) Color.White else Color(0xFF0A0B0E))
                    .border(1.dp, p.err.copy(alpha = 0.65f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Lock, "gated", Modifier.size(13.dp), tint = p.err) }
        }
        Text(app.label, color = p.soft, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
@Composable
fun rememberAppIcon(pkg: String): ImageBitmap? {
    val ctx = LocalContext.current
    return produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) { runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
    }.value
}
