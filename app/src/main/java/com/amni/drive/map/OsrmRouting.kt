package com.amni.drive.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
data class RoutePoint(val lat: Double, val lon: Double)
data class RouteResult(val points: List<RoutePoint>, val segmentSpeeds: List<Double>, val distanceM: Double, val durationS: Double)
object OsrmRouting {
    suspend fun route(waypoints: List<RoutePoint>): RouteResult? = withContext(Dispatchers.IO) {
        if (waypoints.size < 2) return@withContext null
        runCatching {
            val coords = waypoints.joinToString(";") { "${it.lon},${it.lat}" }
            val url = "https://router.project-osrm.org/route/v1/driving/$coords?geometries=geojson&overview=full&annotations=speed"
            val c = URL(url).openConnection() as HttpURLConnection
            c.setRequestProperty("User-Agent", "AmniDrive/0.7 (anthonymr7@gmail.com)")
            c.connectTimeout = 10000
            c.readTimeout = 10000
            val j = JSONObject(c.inputStream.use { it.bufferedReader().readText() })
            if (j.optString("code") != "Ok") return@runCatching null
            val r = j.getJSONArray("routes").getJSONObject(0)
            val cs = r.getJSONObject("geometry").getJSONArray("coordinates")
            val pts = (0 until cs.length()).map { i -> val a = cs.getJSONArray(i); RoutePoint(a.getDouble(1), a.getDouble(0)) }
            val speeds = r.optJSONArray("legs")?.let { legs ->
                (0 until legs.length()).flatMap { li ->
                    val ann = legs.getJSONObject(li).optJSONObject("annotation")?.optJSONArray("speed")
                    if (ann == null) emptyList() else (0 until ann.length()).map { ann.getDouble(it) }
                }
            } ?: emptyList()
            RouteResult(pts, speeds, r.getDouble("distance"), r.getDouble("duration"))
        }.getOrNull()
    }
}
