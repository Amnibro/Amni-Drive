package com.amni.drive.helium
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amni.drive.ui.LocalAmni
import com.amni.drive.ui.UiTheme
import com.amni.drive.weather.WeatherNow
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
@Composable
fun HeliumDrumCockpit(
    theme: UiTheme,
    speedMph: Int,
    weather: WeatherNow?,
    mapContent: @Composable (Modifier) -> Unit,
    mediaContent: @Composable (Modifier) -> Unit,
    appsContent: @Composable (Modifier) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val p = LocalAmni.current
    var angleDeg by remember { mutableFloatStateOf(0f) }
    var velDeg by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var activeSlot by remember { mutableIntStateOf(0) }
    var liveOverlay by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { HeliumCore.ensureFontLoaded(ctx) }
    LaunchedEffect(dragging) {
        var lastNs = 0L
        while (!dragging) {
            withFrameNanos { ns ->
                val dt = if (lastNs == 0L) 0.016f else ((ns - lastNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastNs = ns
                val step = HeliumCore.drumStep(angleDeg, velDeg, dt, false)
                angleDeg = step.angleDeg
                velDeg = step.velDeg
                activeSlot = step.activeIdx
            }
            if (abs(velDeg) < 0.05f && abs((Math.round(angleDeg / 46f) * 46f) - angleDeg) < 0.05f) break
        }
    }
    val tempF = weather?.tempF ?: 72
    val presetIdx = theme.preset.ordinal
    val slotLabels = listOf("AMNI-MAP 3D", "MEDIA", "VOICE & APPS", "VEHICLE")
    val settled = !dragging && abs(velDeg) < 1.2f && abs((Math.round(angleDeg / 46f) * 46f) - angleDeg) < 1.8f
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(p.bg)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true; liveOverlay = false },
                    onVerticalDrag = { _, dragAmount ->
                        val h = size.height.coerceAtLeast(240).toFloat()
                        val dDeg = (-dragAmount / h) * 105f
                        angleDeg += dDeg
                        velDeg = dDeg * 24f
                        activeSlot = (((Math.round(angleDeg / 46f)) % 4) + 4) % 4
                    },
                    onDragEnd = { dragging = false; liveOverlay = true },
                    onDragCancel = { dragging = false; liveOverlay = true }
                )
            }
    ) {
        val rw = (constraints.maxWidth / 2).coerceIn(480, 960)
        val rh = (constraints.maxHeight / 2).coerceIn(270, 540)
        val directBuf = remember(rw, rh) { ByteBuffer.allocateDirect(rw * rh * 4) }
        val bmp = remember(rw, rh) { Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888) }
        if (HeliumCore.available) {
            val wrote = HeliumCore.renderCockpitRgba(directBuf, rw, rh, presetIdx, 10, 0, speedMph, tempF, 5, angleDeg)
            if (wrote == rw * rh * 4) {
                directBuf.rewind()
                bmp.copyPixelsFromBuffer(directBuf)
            }
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "3D Rolling Dash Dial",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        }
        val relDeg = (((activeSlot * 46f - angleDeg) + 92f) % 184f + 184f) % 184f - 92f
        val tiltX = (relDeg * 0.55f).coerceIn(-35f, 35f)
        val scaleY = cos(Math.toRadians(relDeg.toDouble())).toFloat().coerceIn(0.45f, 1f)
        if (settled && liveOverlay && activeSlot != 3) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.76f)
                    .height(maxHeight * 0.62f)
                    .graphicsLayer { rotationX = tiltX; this.scaleY = scaleY; cameraDistance = 14f * density }
                    .clip(RoundedCornerShape(24.dp))
                    .border(2.dp, p.acc, RoundedCornerShape(24.dp))
            ) {
                when (activeSlot) {
                    0 -> mapContent(Modifier.fillMaxSize())
                    1 -> mediaContent(Modifier.fillMaxSize())
                    2 -> appsContent(Modifier.fillMaxSize())
                }
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(p.panel.copy(alpha = 0.92f))
                .border(1.dp, p.line2, RoundedCornerShape(99.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            slotLabels.forEachIndexed { idx, label ->
                val on = idx == activeSlot
                Box(
                    Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (on) p.acc else Color.Transparent)
                        .clickable {
                            dragging = false
                            velDeg = 0f
                            angleDeg = idx * 46f
                            activeSlot = idx
                            liveOverlay = true
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        label,
                        color = if (on) (if (p.isLight) Color.White else Color(0xFF05070A)) else p.soft,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(p.panel2)
                    .clickable { onOpenSettings() }
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text("⚙", color = p.ink, fontSize = 11.sp)
            }
        }
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            repeat(4) { i ->
                Box(
                    Modifier
                        .width(if (i == activeSlot) 10.dp else 7.dp)
                        .height(if (i == activeSlot) 22.dp else 7.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (i == activeSlot) p.acc else p.dim)
                )
            }
        }
    }
}
