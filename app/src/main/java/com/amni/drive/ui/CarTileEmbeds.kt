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
import com.amni.drive.voice.VoiceSearch
import kotlin.math.roundToInt

/**
 * Shared chunky car touch keyboard with live preview, Shift, symbols, voice-to-text, and GO/Enter.
 * Sized to stay comfortable to tap while leaving the target search field visible on wide car screens.
 */
@Composable
fun CarTouchKeyboard(
    preview: String,
    voiceStatus: String?,
    onText: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onVoice: (() -> Unit)? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalAmni.current
    var symMode by remember { mutableStateOf(false) }
    var shiftOn by remember { mutableStateOf(false) }

    val rows = when {
        symMode -> listOf("1234567890", "@._-/:;?!", "+=&%#()'")
        shiftOn -> listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
        else -> listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    }
    val keyH = 28.dp

    Column(
        modifier
            .fillMaxWidth()
            .background(p.panel.copy(alpha = 0.98f))
            .border(1.dp, p.hairline)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val statusTxt = when {
                !voiceStatus.isNullOrBlank() -> "🎤 $voiceStatus"
                preview.isNotEmpty() -> "⌨ $preview"
                else -> "⌨ TAP SEARCH BOX ABOVE, THEN TYPE OR TAP 🎤"
            }
            Text(
                statusTxt,
                color = if (!voiceStatus.isNullOrBlank() || preview.isNotEmpty()) p.acc else p.soft,
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
                    .background(p.panel2)
                    .border(1.dp, p.hairline, RoundedCornerShape(6.dp))
                    .clickable { onClose() }
                    .padding(horizontal = 8.dp, vertical = 1.dp)
            ) {
                Text("HIDE ✕", color = p.ink, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }

        rows.forEachIndexed { rIdx, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (rIdx == 2 && !symMode) {
                    Box(
                        Modifier
                            .weight(1.35f)
                            .height(keyH)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (shiftOn) p.acc else p.panel2)
                            .border(1.dp, if (shiftOn) p.acc else p.hairline, RoundedCornerShape(6.dp))
                            .clickable { shiftOn = !shiftOn },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "⇧",
                            color = if (shiftOn) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                row.forEach { ch ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(keyH)
                            .clip(RoundedCornerShape(6.dp))
                            .background(p.panel2)
                            .border(1.dp, p.hairline, RoundedCornerShape(6.dp))
                            .clickable {
                                onText(ch.toString())
                                if (shiftOn) shiftOn = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            ch.toString(),
                            color = p.ink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (rIdx == 2) {
                    Box(
                        Modifier
                            .weight(1.35f)
                            .height(keyH)
                            .clip(RoundedCornerShape(6.dp))
                            .background(p.panel2)
                            .border(1.dp, p.err.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                            .clickable { onBackspace() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⌫", color = p.err, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(
                Modifier
                    .weight(1.2f)
                    .height(keyH)
                    .clip(RoundedCornerShape(6.dp))
                    .background(p.panel2)
                    .border(1.dp, p.hairline, RoundedCornerShape(6.dp))
                    .clickable { symMode = !symMode },
                contentAlignment = Alignment.Center
            ) {
                Text(if (symMode) "ABC" else "123", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            if (onVoice != null) {
                Box(
                    Modifier
                        .weight(1.1f)
                        .height(keyH)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (!voiceStatus.isNullOrBlank()) p.acc else p.accDim)
                        .border(1.dp, p.acc.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                        .clickable { onVoice() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎤", fontSize = 13.sp)
                }
            }
            Box(
                Modifier
                    .weight(1.1f)
                    .height(keyH)
                    .clip(RoundedCornerShape(6.dp))
                    .background(p.panel2)
                    .border(1.dp, p.hairline, RoundedCornerShape(6.dp))
                    .clickable { onText(".com") },
                contentAlignment = Alignment.Center
            ) {
                Text(".com", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                Modifier
                    .weight(2.8f)
                    .height(keyH)
                    .clip(RoundedCornerShape(6.dp))
                    .background(p.panel2)
                    .border(1.dp, p.hairline, RoundedCornerShape(6.dp))
                    .clickable { onText(" ") },
                contentAlignment = Alignment.Center
            ) {
                Text("SPACE", color = p.soft, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                Modifier
                    .weight(1.5f)
                    .height(keyH)
                    .clip(RoundedCornerShape(6.dp))
                    .background(p.acc)
                    .clickable { onEnter() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "GO ↵",
                    color = if (p.isLight) Color.White else Color(0xFF05070A),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

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
    val fallbackDpi = (LocalDensity.current.density * 160f).roundToInt().coerceIn(185, 250)
    val directReady by CarApps.ready.collectAsState()
    var surfaceInfo by remember { mutableStateOf<Triple<Surface, Int, Int>?>(null) }
    var showKb by remember { mutableStateOf(false) }
    var typedPreview by remember { mutableStateOf("") }
    var voiceStatus by remember { mutableStateOf<String?>(null) }

    fun adaptiveDpi(w: Int, h: Int): Int {
        val m = minOf(w, h).coerceAtLeast(180)
        return if (compactOrb) {
            (m * 0.28f).roundToInt().coerceIn(160, 210)
        } else {
            (m * 0.34f).roundToInt().coerceIn(185, 260)
        }
    }

    fun sendMapText(txt: String) {
        if (txt.isEmpty()) return
        typedPreview = (typedPreview + txt).takeLast(48)
        if (directReady) CarApps.sendText(CarApps.MAP, txt) else CarEmbed.sendText(txt)
    }

    fun sendMapKey(code: Int) {
        if (code == KeyEvent.KEYCODE_DEL) typedPreview = typedPreview.dropLast(1)
        if (code == KeyEvent.KEYCODE_ENTER) typedPreview = ""
        if (directReady) CarApps.sendKeyCode(CarApps.MAP, code) else CarEmbed.sendKeyCode(code)
    }

    fun startMapVoice() {
        VoiceSearch.listen(
            ctx,
            onState = { st -> voiceStatus = st },
            onResult = { spoken ->
                if (!spoken.isNullOrBlank()) {
                    sendMapText(spoken)
                    sendMapKey(KeyEvent.KEYCODE_ENTER)
                    voiceStatus = "Sent “$spoken”"
                    showKb = false
                }
            }
        )
    }

    LaunchedEffect(voiceStatus) {
        val cur = voiceStatus ?: return@LaunchedEffect
        if (!cur.startsWith("Listening") && !cur.startsWith("Hearing") && !cur.startsWith("Processing") && !cur.startsWith("“")) {
            kotlinx.coroutines.delay(3500)
            if (voiceStatus == cur) voiceStatus = null
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
                        .height(30.dp)
                        .background(p.panel)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        "🗺 AMNI-MAP",
                        color = p.acc,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (showKb) p.acc else p.panel2)
                            .border(1.dp, if (showKb) p.acc else p.hairline, RoundedCornerShape(6.dp))
                            .clickable {
                                showKb = !showKb
                                if (!showKb) typedPreview = ""
                            }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "⌨",
                            color = if (showKb) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (voiceStatus != null) p.acc else p.accDim)
                            .border(1.dp, p.acc.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                            .clickable { startMapVoice() }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text("🎤", fontSize = 11.sp)
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(p.panel2)
                            .border(1.dp, p.hairline, RoundedCornerShape(6.dp))
                            .clickable { onSwitchBuiltIn() }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "BUILT-IN",
                            color = p.soft,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
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
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                if (isExpanded) "⤡ EXIT" else "⤢",
                                color = if (isExpanded) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                if (voiceStatus != null && !showKb) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(p.accDim)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "🎤 $voiceStatus",
                            color = p.acc,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                mapSurfaceBox(Modifier.weight(1f).fillMaxWidth())
                if (showKb) {
                    CarTouchKeyboard(
                        preview = typedPreview,
                        voiceStatus = voiceStatus,
                        onText = { sendMapText(it) },
                        onBackspace = { sendMapKey(KeyEvent.KEYCODE_DEL) },
                        onEnter = {
                            sendMapKey(KeyEvent.KEYCODE_ENTER)
                            showKb = false
                        },
                        onVoice = { startMapVoice() },
                        onClose = {
                            showKb = false
                            typedPreview = ""
                        }
                    )
                }
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
    var showKb by remember { mutableStateOf(false) }
    var typedPreview by remember { mutableStateOf("") }
    var voiceStatus by remember { mutableStateOf<String?>(null) }

    fun startAppVoice() {
        VoiceSearch.listen(
            ctx,
            onState = { voiceStatus = it },
            onResult = { spoken ->
                if (!spoken.isNullOrBlank()) {
                    CarApps.sendText(CarApps.MEDIA, spoken)
                    CarApps.sendKeyCode(CarApps.MEDIA, KeyEvent.KEYCODE_ENTER)
                    voiceStatus = "Sent “$spoken”"
                    showKb = false
                }
            }
        )
    }

    LaunchedEffect(voiceStatus) {
        val cur = voiceStatus ?: return@LaunchedEffect
        if (!cur.startsWith("Listening") && !cur.startsWith("Hearing") && !cur.startsWith("Processing") && !cur.startsWith("“")) {
            kotlinx.coroutines.delay(3500)
            if (voiceStatus == cur) voiceStatus = null
        }
    }

    LaunchedEffect(app.packageName) { CarApps.probe() }

    LaunchedEffect(app.packageName, directReady, surfaceInfo, optInMirror) {
        val info = surfaceInfo
        val dpi = if (info != null) (minOf(info.second, info.third) * 0.34f).roundToInt().coerceIn(185, 260) else fallbackDpi
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
                    .height(30.dp)
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
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text("◀", color = p.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    app.label,
                    color = p.ink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (directReady) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (showKb) p.acc else p.panel2)
                            .border(1.dp, if (showKb) p.acc else p.hairline, RoundedCornerShape(6.dp))
                            .clickable {
                                showKb = !showKb
                                if (!showKb) typedPreview = ""
                            }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "⌨",
                            color = if (showKb) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (voiceStatus != null) p.acc else p.accDim)
                            .border(1.dp, p.acc.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                            .clickable { startAppVoice() }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text("🎤", fontSize = 11.sp)
                    }
                }
                if (onToggleExpand != null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isExpanded) p.acc else p.panel2)
                            .border(1.dp, if (isExpanded) p.acc else p.hairline, RoundedCornerShape(6.dp))
                            .clickable { onToggleExpand() }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            if (isExpanded) "⤡" else "⤢",
                            color = if (isExpanded) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
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
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text("APP", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(p.panel2)
                        .border(1.dp, p.hairline, RoundedCornerShape(6.dp))
                        .clickable { onCloseToControls() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text("♪", color = p.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (voiceStatus != null && !showKb) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(p.accDim)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        "🎤 $voiceStatus",
                        color = p.acc,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
            if (showKb && directReady) {
                CarTouchKeyboard(
                    preview = typedPreview,
                    voiceStatus = voiceStatus,
                    onText = { txt ->
                        typedPreview = (typedPreview + txt).takeLast(48)
                        CarApps.sendText(CarApps.MEDIA, txt)
                    },
                    onBackspace = {
                        typedPreview = typedPreview.dropLast(1)
                        CarApps.sendKeyCode(CarApps.MEDIA, KeyEvent.KEYCODE_DEL)
                    },
                    onEnter = {
                        CarApps.sendKeyCode(CarApps.MEDIA, KeyEvent.KEYCODE_ENTER)
                        typedPreview = ""
                        showKb = false
                    },
                    onVoice = { startAppVoice() },
                    onClose = {
                        showKb = false
                        typedPreview = ""
                    }
                )
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
            WebAppEntry("amni-weather", "Amni-Weather", "🌦️", "https://amni-scient.com/weather/", "weather"),
            WebAppEntry("amni-chat", "Amni-Chat Web", "🔒", "https://chat.amni-scient.com", "amniscient.chat"),
            WebAppEntry("amni-learn", "Amni-Learn", "🎓", "https://amni-scient.com/learn/", "amnilearn"),
            WebAppEntry("ace", "Ace Hardware", "🛠️", "https://www.acehardware.com", "acehardware"),
            WebAppEntry("google", "Web Search", "🌐", "https://www.google.com", "chrome")
        )

        fun matchPackage(pkg: String): WebAppEntry? {
            val low = pkg.lowercase()
            return CATALOG.firstOrNull { it.pkgHint.isNotBlank() && low.contains(it.pkgHint) }
        }
    }
}

private const val WEB_INPUT_TRACKER_JS = """
(function(){
  if (window.__amniInputReady) return;
  window.__amniInputReady = true;
  function isEditable(el) {
    if (!el || el.nodeType !== 1) return false;
    var tag = (el.tagName || '').toUpperCase();
    if (tag === 'TEXTAREA') return !el.disabled && !el.readOnly;
    if (tag === 'INPUT') {
      var t = (el.type || 'text').toLowerCase();
      if (['hidden','button','submit','reset','checkbox','radio','file','image','range','color'].indexOf(t) >= 0) return false;
      return !el.disabled && !el.readOnly;
    }
    return !!el.isContentEditable;
  }
  function deepActive() {
    var el = document.activeElement;
    while (el && el.shadowRoot && el.shadowRoot.activeElement) {
      el = el.shadowRoot.activeElement;
    }
    return el;
  }
  function isVisible(el) {
    if (!el || !el.isConnected) return false;
    var r = el.getBoundingClientRect();
    return r.width > 12 && r.height > 10;
  }
  function findInRoot(root) {
    var sel = 'input[name="search_query"],input[type="search"],input[type="text"],input[type="email"],input[type="password"],input[type="url"],input:not([type]),textarea,[contenteditable="true"]';
    var list = root.querySelectorAll ? root.querySelectorAll(sel) : [];
    for (var i = 0; i < list.length; i++) {
      if (isEditable(list[i]) && isVisible(list[i])) return list[i];
    }
    var all = root.querySelectorAll ? root.querySelectorAll('*') : [];
    for (var j = 0; j < all.length; j++) {
      if (all[j].shadowRoot) {
        var found = findInRoot(all[j].shadowRoot);
        if (found) return found;
      }
    }
    return null;
  }
  function remember(ev) {
    var path = ev.composedPath ? ev.composedPath() : [ev.target];
    for (var i = 0; i < path.length; i++) {
      if (isEditable(path[i])) {
        window.__amniLastInput = path[i];
        return;
      }
    }
  }
  document.addEventListener('focusin', remember, true);
  document.addEventListener('click', remember, true);
  document.addEventListener('pointerdown', remember, true);
  document.addEventListener('input', remember, true);
  window.__amniFindTarget = function() {
    var a = deepActive();
    if (isEditable(a) && isVisible(a)) {
      window.__amniLastInput = a;
      return a;
    }
    var last = window.__amniLastInput;
    if (last && isEditable(last) && isVisible(last)) {
      try { last.focus(); } catch(_){}
      return last;
    }
    var found = findInRoot(document);
    if (found) {
      window.__amniLastInput = found;
      try { found.focus(); } catch(_){}
      return found;
    }
    return null;
  };
  window.__amniOpenSearchIfCollapsed = function() {
    var btn = document.querySelector('button[aria-label*="Search" i],button.icon-button.topbar-menu-button-avatar-button,ytm-search-box button,[data-testid="search-link"],a[href="/search"]');
    if (btn && isVisible(btn)) {
      try { btn.click(); return true; } catch(_){}
    }
    return false;
  };
  window.__amniSubmit = function(el) {
    if (!el) el = window.__amniFindTarget();
    if (!el) return;
    try { el.focus(); } catch(_){}
    ['keydown','keypress','keyup'].forEach(function(t){
      el.dispatchEvent(new KeyboardEvent(t, {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true, composed:true, cancelable:true}));
    });
    if (el.form) {
      try {
        if (el.form.requestSubmit) el.form.requestSubmit(); else el.form.submit();
        return;
      } catch(_){}
    }
    var sub = document.querySelector('button[type="submit"],button[aria-label*="Search" i],.searchbox-submit-button,ytm-search-box button[type="submit"]');
    if (sub) { try { sub.click(); } catch(_){} }
  };
})();
"""

private fun installWebInputTracker(wv: android.webkit.WebView?) {
    wv?.evaluateJavascript(WEB_INPUT_TRACKER_JS, null)
}

private fun injectWebText(wv: android.webkit.WebView?, text: String, submitAfter: Boolean = false) {
    if (wv == null) return
    wv.requestFocus()
    val jsLit = org.json.JSONObject.quote(text)
    val js = """
        $WEB_INPUT_TRACKER_JS
        (function(txt, autoGo){
          function doInsert(el) {
            if (!el) return;
            try { el.focus(); } catch(_){}
            if (el.isContentEditable) {
              document.execCommand('insertText', false, txt);
            } else {
              var cur = el.value != null ? String(el.value) : '';
              var s = (typeof el.selectionStart === 'number') ? el.selectionStart : cur.length;
              var e = (typeof el.selectionEnd === 'number') ? el.selectionEnd : cur.length;
              var nv = cur.slice(0, s) + txt + cur.slice(e);
              var proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
              var setter = Object.getOwnPropertyDescriptor(proto, 'value');
              if (setter && setter.set) setter.set.call(el, nv); else el.value = nv;
              var pos = s + txt.length;
              try { el.setSelectionRange(pos, pos); } catch(_){}
              try {
                el.dispatchEvent(new InputEvent('input', {bubbles:true, composed:true, inputType:'insertText', data:txt}));
              } catch(_) {
                el.dispatchEvent(new Event('input', {bubbles:true, composed:true}));
              }
              el.dispatchEvent(new Event('change', {bubbles:true, composed:true}));
            }
            if (autoGo) {
              setTimeout(function(){ window.__amniSubmit && window.__amniSubmit(el); }, 110);
            }
          }
          var el = window.__amniFindTarget();
          if (el) {
            doInsert(el);
          } else if (window.__amniOpenSearchIfCollapsed()) {
            setTimeout(function(){ doInsert(window.__amniFindTarget()); }, 240);
          }
        })($jsLit, $submitAfter);
    """.trimIndent()
    wv.evaluateJavascript(js, null)
}

private fun injectWebBackspace(wv: android.webkit.WebView?) {
    if (wv == null) return
    wv.requestFocus()
    val js = """
        $WEB_INPUT_TRACKER_JS
        (function(){
          var el = window.__amniFindTarget();
          if (!el) return;
          try { el.focus(); } catch(_){}
          if (el.isContentEditable) {
            document.execCommand('delete', false, null);
          } else if (el.value != null && el.value.length > 0) {
            var cur = String(el.value);
            var s = (typeof el.selectionStart === 'number') ? el.selectionStart : cur.length;
            var e = (typeof el.selectionEnd === 'number') ? el.selectionEnd : cur.length;
            var nv = (s === e && s > 0) ? cur.slice(0, s - 1) + cur.slice(e) : cur.slice(0, s) + cur.slice(e);
            var pos = (s === e && s > 0) ? s - 1 : s;
            var proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
            var setter = Object.getOwnPropertyDescriptor(proto, 'value');
            if (setter && setter.set) setter.set.call(el, nv); else el.value = nv;
            try { el.setSelectionRange(pos, pos); } catch(_){}
            try {
              el.dispatchEvent(new InputEvent('input', {bubbles:true, composed:true, inputType:'deleteContentBackward'}));
            } catch(_) {
              el.dispatchEvent(new Event('input', {bubbles:true, composed:true}));
            }
          }
        })();
    """.trimIndent()
    wv.evaluateJavascript(js, null)
}

private fun injectWebEnter(wv: android.webkit.WebView?) {
    if (wv == null) return
    wv.requestFocus()
    val js = """
        $WEB_INPUT_TRACKER_JS
        (function(){ window.__amniSubmit && window.__amniSubmit(window.__amniFindTarget()); })();
    """.trimIndent()
    wv.evaluateJavascript(js, null)
    runCatching {
        wv.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        wv.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }
}

/**
 * Zero-ADB, zero-screenshare, native in-car WebView media/app box with built-in car touch keyboard,
 * voice-to-text search, and shared CookieManager sessions from WebLoginActivity.
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
    var typedPreview by remember { mutableStateOf("") }
    var voiceStatus by remember { mutableStateOf<String?>(null) }
    var loginBanner by remember { mutableStateOf<String?>(null) }

    fun startWebVoice() {
        VoiceSearch.listen(
            ctx,
            onState = { voiceStatus = it },
            onResult = { spoken ->
                if (!spoken.isNullOrBlank()) {
                    typedPreview = ""
                    injectWebText(webRef, spoken, submitAfter = true)
                    voiceStatus = "Searched “$spoken”"
                    showKb = false
                }
            }
        )
    }

    LaunchedEffect(voiceStatus) {
        val cur = voiceStatus ?: return@LaunchedEffect
        if (!cur.startsWith("Listening") && !cur.startsWith("Hearing") && !cur.startsWith("Processing") && !cur.startsWith("“")) {
            kotlinx.coroutines.delay(3500)
            if (voiceStatus == cur) voiceStatus = null
        }
    }

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
                    .height(30.dp)
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
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) { Text("◀", color = p.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(p.panel2)
                        .border(1.dp, p.hairline, RoundedCornerShape(6.dp))
                        .clickable { webRef?.reload() }
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                ) { Text("↻", color = p.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                Text(
                    "${app.emoji} $pageTitle",
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
                        .background(if (showKb) p.acc else p.panel2)
                        .border(1.dp, if (showKb) p.acc else p.hairline, RoundedCornerShape(6.dp))
                        .clickable {
                            showKb = !showKb
                            if (showKb) installWebInputTracker(webRef) else typedPreview = ""
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        "⌨",
                        color = if (showKb) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (voiceStatus != null) p.acc else p.accDim)
                        .border(1.dp, p.acc.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                        .clickable { startWebVoice() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) { Text("🎤", fontSize = 11.sp) }
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
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                ) { Text("🔑", fontSize = 10.sp) }
                if (onToggleExpand != null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isExpanded) p.acc else p.panel2)
                            .border(1.dp, if (isExpanded) p.acc else p.hairline, RoundedCornerShape(6.dp))
                            .clickable { onToggleExpand() }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            if (isExpanded) "⤡" else "⤢",
                            color = if (isExpanded) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.ink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
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
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) { Text("APP", color = p.acc, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(p.panel2)
                        .border(1.dp, p.hairline, RoundedCornerShape(6.dp))
                        .clickable { onCloseToControls() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) { Text("♪", color = p.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }

            (voiceStatus?.takeIf { !showKb }?.let { "🎤 $it" } ?: loginBanner)?.let { banner ->
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
                        fontSize = 9.5.sp,
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
                        isFocusable = true
                        isFocusableInTouchMode = true
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.textZoom = 115
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
                                installWebInputTracker(view)
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
                CarTouchKeyboard(
                    preview = typedPreview,
                    voiceStatus = voiceStatus,
                    onText = { txt ->
                        typedPreview = (typedPreview + txt).takeLast(48)
                        injectWebText(webRef, txt)
                    },
                    onBackspace = {
                        typedPreview = typedPreview.dropLast(1)
                        injectWebBackspace(webRef)
                    },
                    onEnter = {
                        injectWebEnter(webRef)
                        typedPreview = ""
                        showKb = false
                    },
                    onVoice = { startWebVoice() },
                    onClose = {
                        showKb = false
                        typedPreview = ""
                    }
                )
            }
        }
    }
}
