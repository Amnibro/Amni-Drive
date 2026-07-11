package com.amni.drive.ui
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.amni.drive.weather.Wx
import kotlinx.coroutines.isActive
import kotlin.math.sin
@Composable
fun WeatherLayer(wx: Wx, calm: Boolean, modifier: Modifier = Modifier) {
    if (wx != Wx.RAIN && wx != Wx.THUNDER && wx != Wx.SNOW && wx != Wx.HAIL) return
    var t by remember { mutableLongStateOf(0L) }
    LaunchedEffect(wx) { while (isActive) androidx.compose.runtime.withFrameNanos { t = it } }
    Canvas(modifier) {
        val sec = t / 1.0e9
        val w = size.width
        val h = size.height
        val n = (if (wx == Wx.SNOW) 70 else 110) / (if (calm) 2 else 1)
        val drop = Color(0xFFA8C6E8)
        for (i in 0 until n) {
            val r1 = (((i * 2654435761L) ushr 8) % 1000L) / 1000f
            val r2 = (((i * 40503L + 977L) * 65537L ushr 6) % 1000L) / 1000f
            val r3 = (((i * 97L + 13L) * 8191L ushr 4) % 1000L) / 1000f
            when (wx) {
                Wx.SNOW -> {
                    val speed = (30f + r2 * 40f) * (if (calm) 0.6f else 1f)
                    val y = ((r1 * h + (sec * speed).toFloat()) % (h + 20f)) - 10f
                    val x = (r2 * w + sin(sec * 0.7 + i).toFloat() * 24f + w) % w
                    drawCircle(Color.White.copy(alpha = 0.18f + r3 * 0.25f), 1.5.dp.toPx() + r3 * 2.dp.toPx(), Offset(x, y))
                }
                Wx.HAIL -> {
                    val speed = 600f + r2 * 300f
                    val y = ((r1 * h + (sec * speed).toFloat()) % (h + 30f)) - 15f
                    val x = (r2 * w + (sec * speed * 0.15).toFloat()) % w
                    drawCircle(Color(0xFFDCEAFF).copy(alpha = 0.25f + r3 * 0.3f), 1.dp.toPx() + r3 * 1.5.dp.toPx(), Offset(x, y))
                }
                else -> {
                    val speed = (500f + r2 * 350f) * (if (calm) 0.7f else 1f)
                    val len = (24f + r3 * 60f) * density
                    val y = ((r1 * h + (sec * speed).toFloat()) % (h + len * 2)) - len
                    val x = (r2 * w + (sec * speed * 0.36).toFloat()) % w
                    drawLine(drop.copy(alpha = 0.04f + r3 * 0.08f), Offset(x, y), Offset(x + len * 0.36f, y + len * 0.93f), strokeWidth = (0.8f + r3) * density, cap = StrokeCap.Round)
                }
            }
        }
        if (wx == Wx.THUNDER && !calm && (sec % 6.5) < 0.12) drawRect(Color(0xFFFFF0C0).copy(alpha = 0.05f))
    }
}
