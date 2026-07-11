package com.amni.drive.map
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
class TileStore(context: Context) {
    private val mem = LruCache<String, Bitmap>(48)
    private val dir = File(context.cacheDir, "tiles").apply { mkdirs() }
    suspend fun prefetch(basemap: Int, lat: Double, lon: Double, onProgress: (Int, Int) -> Unit) = withContext(Dispatchers.IO) {
        val plan = mutableListOf<Triple<Int, Int, Int>>()
        listOf(11 to 1, 12 to 2, 13 to 3, 14 to 4, 15 to 5, 16 to 5).forEach { (z, r) ->
            val cx = GeoMath.lonToTileX(lon, z).toInt()
            val cy = GeoMath.latToTileY(lat, z).toInt()
            for (x in cx - r..cx + r) for (y in cy - r..cy + r) plan.add(Triple(z, x, y))
        }
        var done = 0
        plan.forEach { (z, x, y) -> tile(basemap, z, x, y); done++; onProgress(done, plan.size) }
    }
    suspend fun tile(basemap: Int, z: Int, x: Int, y: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "${basemap}_${z}_${x}_$y"
        mem.get(key)?.let { return@withContext it }
        val f = File(dir, "$key.png")
        if (f.exists()) runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()?.let { mem.put(key, it); return@withContext it }
        val url = when (basemap) {
            2 -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
            1 -> "https://basemaps.cartocdn.com/light_all/$z/$x/$y@2x.png"
            else -> "https://basemaps.cartocdn.com/dark_all/$z/$x/$y@2x.png"
        }
        runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.setRequestProperty("User-Agent", "AmniDrive/0.6 (anthonymr7@gmail.com)")
            c.connectTimeout = 8000
            c.readTimeout = 8000
            val bmp = c.inputStream.use { BitmapFactory.decodeStream(it) }
            if (bmp != null) { mem.put(key, bmp); runCatching { f.outputStream().use { o -> bmp.compress(Bitmap.CompressFormat.PNG, 90, o) } } }
            bmp
        }.getOrNull()
    }
}
