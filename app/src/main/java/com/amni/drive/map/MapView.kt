package com.amni.drive.map
import android.location.Location
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
class MapCamera(lat: Double, lon: Double) {
    var lat by mutableDoubleStateOf(lat)
    var lon by mutableDoubleStateOf(lon)
    var zoom by mutableIntStateOf(15)
    var follow by mutableStateOf(true)
    var northUp by mutableStateOf(true)
    var tilt by mutableStateOf(false)
    var basemap by mutableIntStateOf(0)
}
@Composable
fun rememberMapCamera(): MapCamera = remember { MapCamera(30.2672, -97.7431) }
@Composable
fun AmniMap(
    camera: MapCamera,
    tiles: TileStore,
    route: RouteResult?,
    reports: List<Report>,
    stops: List<RoutePoint>,
    destination: RoutePoint?,
    location: Location?,
    accent: Color,
    panel: Color,
    onLongPress: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val tilePx = with(density) { 230.dp.toPx() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val bitmaps = remember { mutableStateMapOf<String, ImageBitmap>() }
    val inflight = remember { mutableSetOf<String>() }
    val emojiPaint = remember { android.graphics.Paint().apply { textAlign = android.graphics.Paint.Align.CENTER; textSize = tilePx * 0.085f } }
    val numPaint = remember { android.graphics.Paint().apply { textAlign = android.graphics.Paint.Align.CENTER; textSize = tilePx * 0.075f; color = android.graphics.Color.BLACK; isFakeBoldText = true } }
    LaunchedEffect(location, camera.follow) { if (camera.follow) location?.let { camera.lat = it.latitude; camera.lon = it.longitude } }
    LaunchedEffect(camera.lat, camera.lon, camera.zoom, camera.basemap, size) {
        if (size == IntSize.Zero) return@LaunchedEffect
        val z = camera.zoom
        val cx = GeoMath.lonToTileX(camera.lon, z)
        val cy = GeoMath.latToTileY(camera.lat, z)
        val hx = size.width / tilePx / 2.0 + 1.2
        val hy = size.height / tilePx / 2.0 + 1.5
        val max = 2.0.pow(z).toInt() - 1
        val needed = mutableListOf<Triple<String, Int, Int>>()
        for (tx in floor(cx - hx).toInt()..ceil(cx + hx).toInt()) for (ty in floor(cy - hy).toInt()..ceil(cy + hy).toInt()) {
            if (tx in 0..max && ty in 0..max) needed.add(Triple("${camera.basemap}_${z}_${tx}_$ty", tx, ty))
        }
        val neededKeys = needed.map { it.first }.toSet()
        if (bitmaps.size > 90) bitmaps.keys.filterNot { it in neededKeys }.forEach { bitmaps.remove(it) }
        needed.forEach { (key, tx, ty) ->
            if (!bitmaps.containsKey(key) && inflight.add(key)) scope.launch {
                tiles.tile(camera.basemap, z, tx, ty)?.let { bitmaps[key] = it.asImageBitmap() }
                inflight.remove(key)
            }
        }
    }
    Box(modifier.onSizeChanged { size = it }.graphicsLayer {
        if (camera.tilt) { rotationX = 38f; scaleX = 1.45f; scaleY = 1.45f; cameraDistance = 10f * density.density }
        if (!camera.northUp) rotationZ = -(location?.bearing ?: 0f)
    }) {
        Canvas(Modifier.fillMaxSize()
            .pointerInput(tilePx) {
                detectDragGestures { change, drag ->
                    change.consume()
                    camera.follow = false
                    val z = camera.zoom
                    camera.lon = GeoMath.tileXToLon(GeoMath.lonToTileX(camera.lon, z) - drag.x / tilePx, z)
                    camera.lat = GeoMath.tileYToLat((GeoMath.latToTileY(camera.lat, z) - drag.y / tilePx).coerceIn(0.0, 2.0.pow(z)), z)
                }
            }
            .pointerInput(tilePx) {
                detectTapGestures(onLongPress = { off ->
                    val z = camera.zoom
                    val la = GeoMath.tileYToLat(GeoMath.latToTileY(camera.lat, z) + (off.y - this.size.height / 2f) / tilePx, z)
                    val lo = GeoMath.tileXToLon(GeoMath.lonToTileX(camera.lon, z) + (off.x - this.size.width / 2f) / tilePx, z)
                    onLongPress(la, lo)
                })
            }
        ) {
            val z = camera.zoom
            val cx = GeoMath.lonToTileX(camera.lon, z)
            val cy = GeoMath.latToTileY(camera.lat, z)
            val w = this.size.width
            val h = this.size.height
            fun toScreen(lat: Double, lon: Double) = Offset(((GeoMath.lonToTileX(lon, z) - cx) * tilePx + w / 2f).toFloat(), ((GeoMath.latToTileY(lat, z) - cy) * tilePx + h / 2f).toFloat())
            for (tx in floor(cx - w / tilePx / 2.0 - 1).toInt()..ceil(cx + w / tilePx / 2.0 + 1).toInt()) for (ty in floor(cy - h / tilePx / 2.0 - 1).toInt()..ceil(cy + h / tilePx / 2.0 + 1).toInt()) {
                val dx = ((tx - cx) * tilePx + w / 2f).roundToInt()
                val dy = ((ty - cy) * tilePx + h / 2f).roundToInt()
                val sz = ceil(tilePx).toInt() + 1
                val bmp = bitmaps["${camera.basemap}_${z}_${tx}_$ty"]
                if (bmp != null) drawImage(bmp, srcOffset = IntOffset.Zero, srcSize = IntSize(bmp.width, bmp.height), dstOffset = IntOffset(dx, dy), dstSize = IntSize(sz, sz))
                else drawRect(panel, topLeft = Offset(dx.toFloat(), dy.toFloat()), size = Size(sz.toFloat(), sz.toFloat()))
            }
            if (camera.basemap != 2) drawRect(accent.copy(alpha = 0.05f))
            route?.let { r ->
                if (r.points.size > 1) {
                    val pts = r.points.map { toScreen(it.lat, it.lon) }
                    val path = Path().apply { moveTo(pts[0].x, pts[0].y); for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y) }
                    drawPath(path, Color.Black.copy(alpha = 0.55f), style = Stroke(13.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(path, accent.copy(alpha = 0.16f), style = Stroke(20.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    if (r.segmentSpeeds.isNotEmpty()) {
                        val n = pts.size - 1
                        for (i in 0 until n) {
                            val s = r.segmentSpeeds.getOrElse(i) {
                                r.segmentSpeeds[((i.toDouble() / n.coerceAtLeast(1)) * r.segmentSpeeds.size).toInt().coerceIn(0, r.segmentSpeeds.lastIndex)]
                            }
                            val col = when {
                                s < 2.5 -> Color(0xFFFF3B3B)
                                s < 7.0 -> Color(0xFFFF8A3D)
                                s < 13.0 -> Color(0xFFFFD54A)
                                else -> accent
                            }
                            drawLine(col, pts[i], pts[i + 1], strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
                        }
                    } else drawPath(path, accent, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            stops.dropLast(1).forEachIndexed { i, s ->
                val p = toScreen(s.lat, s.lon)
                drawCircle(accent, 10.dp.toPx(), p)
                drawCircle(Color(0xFF0A0B0E), 10.dp.toPx(), p, style = Stroke(2.5.dp.toPx()))
                drawContext.canvas.nativeCanvas.drawText("${i + 1}", p.x, p.y + numPaint.textSize * 0.35f, numPaint)
            }
            destination?.let { d ->
                val p = toScreen(d.lat, d.lon)
                val tip = Path().apply { moveTo(p.x - 7.dp.toPx(), p.y - 8.dp.toPx()); lineTo(p.x, p.y + 4.dp.toPx()); lineTo(p.x + 7.dp.toPx(), p.y - 8.dp.toPx()); close() }
                drawPath(tip, accent)
                drawCircle(accent, 11.dp.toPx(), Offset(p.x, p.y - 14.dp.toPx()))
                drawCircle(Color(0xFF0A0B0E), 4.dp.toPx(), Offset(p.x, p.y - 14.dp.toPx()))
            }
            reports.forEach { rp ->
                val p = toScreen(rp.lat, rp.lon)
                drawCircle(Color(0xFFFFB454), 11.dp.toPx(), p)
                drawCircle(Color(0xFF0A0B0E), 11.dp.toPx(), p, style = Stroke(2.dp.toPx()))
                drawContext.canvas.nativeCanvas.drawText(rp.emoji, p.x, p.y + emojiPaint.textSize * 0.35f, emojiPaint)
            }
            location?.let { l ->
                val p = toScreen(l.latitude, l.longitude)
                drawCircle(accent.copy(alpha = 0.15f), 22.dp.toPx(), p)
                if (l.hasBearing() && l.speed > 1f) rotate(l.bearing, p) {
                    val cone = Path().apply { moveTo(p.x, p.y); lineTo(p.x - 12.dp.toPx(), p.y - 32.dp.toPx()); lineTo(p.x + 12.dp.toPx(), p.y - 32.dp.toPx()); close() }
                    drawPath(cone, accent.copy(alpha = 0.30f))
                }
                drawCircle(accent, 9.dp.toPx(), p)
                drawCircle(Color(0xFF0A0B0E), 9.dp.toPx(), p, style = Stroke(3.dp.toPx()))
            }
        }
    }
}
