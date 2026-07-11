package com.amni.drive.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
data class Place(val name: String, val detail: String, val lat: Double, val lon: Double)
object Geocoder {
    suspend fun search(query: String, lat: Double?, lon: Double?): List<Place> = withContext(Dispatchers.IO) {
        val photon = runCatching { photon(query, lat, lon) }.getOrDefault(emptyList())
        if (photon.isNotEmpty()) photon else runCatching { nominatim(query) }.getOrDefault(emptyList())
    }
    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "AmniDrive/0.7 (anthonymr7@gmail.com)")
        c.connectTimeout = 8000
        c.readTimeout = 8000
        return c.inputStream.use { it.bufferedReader().readText() }
    }
    private fun photon(query: String, lat: Double?, lon: Double?): List<Place> {
        val bias = if (lat != null && lon != null) "&lat=$lat&lon=$lon" else ""
        val j = JSONObject(get("https://photon.komoot.io/api/?q=${URLEncoder.encode(query, "UTF-8")}&limit=6$bias"))
        val feats = j.getJSONArray("features")
        return (0 until feats.length()).mapNotNull { i ->
            val f = feats.getJSONObject(i)
            val p = f.getJSONObject("properties")
            val g = f.getJSONObject("geometry").getJSONArray("coordinates")
            val name = p.optString("name").ifEmpty { listOf(p.optString("housenumber"), p.optString("street")).filter { it.isNotEmpty() }.joinToString(" ") }
            if (name.isEmpty()) null else Place(name, listOf(p.optString("street"), p.optString("city"), p.optString("state")).filter { it.isNotEmpty() && it != name }.joinToString(", "), g.getDouble(1), g.getDouble(0))
        }
    }
    private fun nominatim(query: String): List<Place> {
        val arr = org.json.JSONArray(get("https://nominatim.openstreetmap.org/search?q=${URLEncoder.encode(query, "UTF-8")}&format=json&limit=6"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val parts = o.getString("display_name").split(", ")
            Place(parts.firstOrNull() ?: "?", parts.drop(1).take(3).joinToString(", "), o.getString("lat").toDouble(), o.getString("lon").toDouble())
        }
    }
}
