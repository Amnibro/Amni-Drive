package com.amni.drive.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
data class RoutePoint(val lat: Double, val lon: Double)
data class RouteStep(val type: String, val modifier: String, val name: String, val lat: Double, val lon: Double, val distanceM: Double)
data class RouteResult(val points: List<RoutePoint>, val segmentSpeeds: List<Double>, val distanceM: Double, val durationS: Double, val steps: List<RouteStep> = emptyList())
object OsrmRouting {
    private const val TILE_Z = 15
    private const val TTL_MS = 20 * 60_000L
    private const val MAX_CACHE = 48

    private data class CachedRoute(
        val originLat: Double,
        val originLon: Double,
        val route: RouteResult,
        val storedAt: Long
    )

    private val corridorCache = java.util.concurrent.ConcurrentHashMap<String, CachedRoute>()
    private val warmScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private var warmJob: kotlinx.coroutines.Job? = null

    private fun destKey(waypoints: List<RoutePoint>): String =
        waypoints.drop(1).joinToString(";") { "${(it.lat * 1000).toInt()},${(it.lon * 1000).toInt()}" }

    private fun tileKey(lat: Double, lon: Double, dk: String): String {
        val tx = kotlin.math.floor(GeoMath.lonToTileX(lon, TILE_Z)).toInt()
        val ty = kotlin.math.floor(GeoMath.latToTileY(lat, TILE_Z)).toInt()
        return "$tx|$ty|$dk"
    }

    private fun cacheHit(origin: RoutePoint, dk: String): RouteResult? {
        val now = System.currentTimeMillis()
        val cx = kotlin.math.floor(GeoMath.lonToTileX(origin.lon, TILE_Z)).toInt()
        val cy = kotlin.math.floor(GeoMath.latToTileY(origin.lat, TILE_Z)).toInt()
        var best: CachedRoute? = null
        var bestDist = 250.0
        for (dx in -1..1) {
            for (dy in -1..1) {
                val e = corridorCache["${cx + dx}|${cy + dy}|$dk"] ?: continue
                if (now - e.storedAt > TTL_MS) continue
                val d = haversineM(origin, RoutePoint(e.originLat, e.originLon))
                if (d < bestDist) {
                    bestDist = d
                    best = e
                }
            }
        }
        return best?.route
    }

    private fun warmCorridor(res: RouteResult, targets: List<RoutePoint>, dk: String) {
        val steps = res.steps
        if (steps.size < 2 || targets.isEmpty()) return
        val candidates = mutableListOf<RoutePoint>()
        for (i in 1 until minOf(steps.size, 4)) {
            val st = steps[i]
            if (st.type == "arrive") break
            val prev = steps[i - 1]
            val dLonR = Math.toRadians(st.lon - prev.lon)
            val la1 = Math.toRadians(prev.lat)
            val la2 = Math.toRadians(st.lat)
            val bearingRad = atan2(sin(dLonR) * cos(la2), cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dLonR))
            val dLat = (140.0 / 111_111.0) * cos(bearingRad)
            val dLon = (140.0 / (111_111.0 * cos(Math.toRadians(st.lat)).coerceAtLeast(0.2))) * sin(bearingRad)
            candidates.add(RoutePoint(st.lat + dLat, st.lon + dLon))
        }
        if (candidates.isEmpty()) return
        warmJob?.cancel()
        warmJob = warmScope.launch {
            kotlinx.coroutines.delay(1500L)
            candidates.forEach { pt ->
                val k = tileKey(pt.lat, pt.lon, dk)
                val existing = corridorCache[k]
                if (existing != null && System.currentTimeMillis() - existing.storedAt < TTL_MS) return@forEach
                val r = fetchRoute(listOf(pt) + targets) ?: return@forEach
                if (corridorCache.size >= MAX_CACHE) {
                    corridorCache.entries.minByOrNull { it.value.storedAt }?.key?.let { corridorCache.remove(it) }
                }
                corridorCache[k] = CachedRoute(pt.lat, pt.lon, r, System.currentTimeMillis())
            }
        }
    }

    suspend fun route(waypoints: List<RoutePoint>): RouteResult? = withContext(Dispatchers.IO) {
        if (waypoints.size < 2) return@withContext null
        val dk = destKey(waypoints)
        cacheHit(waypoints.first(), dk)?.let { return@withContext it }
        val res = fetchRoute(waypoints) ?: return@withContext null
        corridorCache[tileKey(waypoints.first().lat, waypoints.first().lon, dk)] =
            CachedRoute(waypoints.first().lat, waypoints.first().lon, res, System.currentTimeMillis())
        warmCorridor(res, waypoints.drop(1), dk)
        res
    }

    private fun fetchRoute(waypoints: List<RoutePoint>): RouteResult? = runCatching {
        val coords = waypoints.joinToString(";") { "${it.lon},${it.lat}" }
        val url = "https://router.project-osrm.org/route/v1/driving/$coords?geometries=geojson&overview=full&annotations=speed,distance&steps=true"
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "AmniDrive/1.10 (https://amni-scient.com)")
        c.connectTimeout = 12000
        c.readTimeout = 12000
        val j = JSONObject(c.inputStream.use { it.bufferedReader().readText() })
        if (j.optString("code") != "Ok") return@runCatching null
        val r = j.getJSONArray("routes").getJSONObject(0)
        val cs = r.getJSONObject("geometry").getJSONArray("coordinates")
        val pts = (0 until cs.length()).map { i -> val a = cs.getJSONArray(i); RoutePoint(a.getDouble(1), a.getDouble(0)) }
val rawSpeeds = ArrayList<Double>()
val rawDists = ArrayList<Double>()
val steps = ArrayList<RouteStep>()
r.optJSONArray("legs")?.let { legs ->
for (li in 0 until legs.length()) {
legs.getJSONObject(li).optJSONArray("steps")?.let { st ->
for (si in 0 until st.length()) {
val s = st.getJSONObject(si)
val m = s.optJSONObject("maneuver") ?: continue
val loc = m.optJSONArray("location") ?: continue
steps.add(RouteStep(m.optString("type"), m.optString("modifier"), s.optString("name"), loc.getDouble(1), loc.getDouble(0), s.optDouble("distance", 0.0)))
}
}
val ann = legs.getJSONObject(li).optJSONObject("annotation") ?: continue
val sp = ann.optJSONArray("speed")
val di = ann.optJSONArray("distance")
if (sp != null) for (i in 0 until sp.length()) rawSpeeds.add(sp.optDouble(i, Double.NaN))
if (di != null) for (i in 0 until di.length()) rawDists.add(di.optDouble(i, 0.0))
}
}
val speeds = expandToGeometry(pts, rawSpeeds, rawDists)
RouteResult(pts, speeds, r.getDouble("distance"), r.getDouble("duration"), steps)
}.getOrNull()
private fun expandToGeometry(pts: List<RoutePoint>, speeds: List<Double>, dists: List<Double>): List<Double> {
if (pts.size < 2) return emptyList()
val nSeg = pts.size - 1
if (speeds.isEmpty()) return List(nSeg) { 15.0 }
if (speeds.size == nSeg) return speeds.map { if (it.isNaN()) 10.0 else it }
val clean = speeds.map { if (it.isNaN() || it < 0) 5.0 else it }
if (dists.size == clean.size && dists.sum() > 1.0) {
val out = ArrayList<Double>(nSeg)
var ai = 0
var aLeft = dists[0]
for (i in 0 until nSeg) {
val g = haversineM(pts[i], pts[i + 1]).coerceAtLeast(0.5)
var need = g
var acc = 0.0
var w = 0.0
while (need > 1e-3 && ai < clean.size) {
val take = minOf(need, aLeft)
acc += clean[ai] * take
w += take
need -= take
aLeft -= take
if (aLeft <= 1e-3) {
ai++
if (ai < clean.size) aLeft = dists[ai].coerceAtLeast(0.5)
}
}
out.add(if (w > 0) acc / w else clean[ai.coerceAtMost(clean.lastIndex)])
}
return out
}
return (0 until nSeg).map { i ->
val idx = ((i.toDouble() / nSeg) * clean.size).toInt().coerceIn(0, clean.lastIndex)
clean[idx]
}
}
private fun haversineM(a: RoutePoint, b: RoutePoint): Double {
val r = 6371000.0
val dLat = Math.toRadians(b.lat - a.lat)
val dLon = Math.toRadians(b.lon - a.lon)
val x = sin(dLat / 2).let { it * it } + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
return 2 * r * atan2(sqrt(x), sqrt(1 - x))
}
}
