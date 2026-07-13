package com.amni.drive.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
data class RoutePoint(val lat: Double, val lon: Double)
data class RouteResult(val points: List<RoutePoint>, val segmentSpeeds: List<Double>, val distanceM: Double, val durationS: Double)
object OsrmRouting {
suspend fun route(waypoints: List<RoutePoint>): RouteResult? = withContext(Dispatchers.IO) {
if (waypoints.size < 2) return@withContext null
runCatching {
val coords = waypoints.joinToString(";") { "${it.lon},${it.lat}" }
val url = "https://router.project-osrm.org/route/v1/driving/$coords?geometries=geojson&overview=full&annotations=speed,distance"
val c = URL(url).openConnection() as HttpURLConnection
c.setRequestProperty("User-Agent", "AmniDrive/1.6 (personal)")
c.connectTimeout = 12000
c.readTimeout = 12000
val j = JSONObject(c.inputStream.use { it.bufferedReader().readText() })
if (j.optString("code") != "Ok") return@runCatching null
val r = j.getJSONArray("routes").getJSONObject(0)
val cs = r.getJSONObject("geometry").getJSONArray("coordinates")
val pts = (0 until cs.length()).map { i -> val a = cs.getJSONArray(i); RoutePoint(a.getDouble(1), a.getDouble(0)) }
val rawSpeeds = ArrayList<Double>()
val rawDists = ArrayList<Double>()
r.optJSONArray("legs")?.let { legs ->
for (li in 0 until legs.length()) {
val ann = legs.getJSONObject(li).optJSONObject("annotation") ?: continue
val sp = ann.optJSONArray("speed")
val di = ann.optJSONArray("distance")
if (sp != null) for (i in 0 until sp.length()) rawSpeeds.add(sp.optDouble(i, Double.NaN))
if (di != null) for (i in 0 until di.length()) rawDists.add(di.optDouble(i, 0.0))
}
}
val speeds = expandToGeometry(pts, rawSpeeds, rawDists)
RouteResult(pts, speeds, r.getDouble("distance"), r.getDouble("duration"))
}.getOrNull()
}
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
