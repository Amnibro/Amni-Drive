package com.amni.drive.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

data class Place(val name: String, val detail: String, val lat: Double, val lon: Double)

object Geocoder {
    suspend fun search(query: String, lat: Double?, lon: Double?): List<Place> = withContext(Dispatchers.IO) {
        val custom = CustomPlaces.search(query, lat, lon)
        val photon = runCatching { photon(query, lat, lon) }.getOrDefault(emptyList())
        val backup = if (photon.size < 3) {
            coroutineScope {
                listOf(
                    async { runCatching { nominatim(query) }.getOrDefault(emptyList()) },
                    async { runCatching { wikiSearch(query, lat, lon) }.getOrDefault(emptyList()) }
                ).awaitAll().flatten()
            }
        } else emptyList()
        (custom.take(4) + photon + backup)
            .distinctBy { "${(it.lat * 2000).roundToInt()}|${(it.lon * 2000).roundToInt()}" }
            .take(12)
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "AmniDrive/1.10 (https://amni-scient.com)")
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
            val house = p.optString("housenumber").trim()
            val street = p.optString("street").trim()
            val streetAddr = listOf(house, street).filter { it.isNotEmpty() }.joinToString(" ")
            val rawName = p.optString("name").trim()
            val name = when {
                rawName.isEmpty() -> streetAddr
                streetAddr.isNotEmpty() && (rawName == house || rawName.all { it.isDigit() }) -> streetAddr
                else -> rawName
            }
            if (name.isEmpty()) null else Place(
                name,
                listOf(streetAddr, p.optString("city"), p.optString("state"))
                    .filter { it.isNotEmpty() && it != name }
                    .joinToString(", "),
                g.getDouble(1),
                g.getDouble(0)
            )
        }
    }

    private fun nominatim(query: String): List<Place> {
        val arr = JSONArray(get("https://nominatim.openstreetmap.org/search?q=${URLEncoder.encode(query, "UTF-8")}&format=json&limit=6"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val parts = o.getString("display_name").split(", ")
            Place(
                parts.firstOrNull() ?: "?",
                parts.drop(1).take(3).joinToString(", "),
                o.getString("lat").toDouble(),
                o.getString("lon").toDouble()
            )
        }
    }

    private fun wikiSearch(query: String, lat: Double?, lon: Double?): List<Place> {
        val url = "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=${URLEncoder.encode(query, "UTF-8")}&gsrlimit=5&prop=coordinates&format=json"
        val pages = JSONObject(get(url)).optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
        val out = mutableListOf<Place>()
        val keys = pages.keys()
        while (keys.hasNext()) {
            val o = pages.getJSONObject(keys.next())
            val c = o.optJSONArray("coordinates")?.optJSONObject(0) ?: continue
            val la = c.optDouble("lat", Double.NaN)
            val lo = c.optDouble("lon", Double.NaN)
            if (la.isNaN() || lo.isNaN()) continue
            if (lat != null && lon != null && GeoMath.haversineM(lat, lon, la, lo) > 250_000.0) continue
            out.add(Place(o.optString("title"), "Wikipedia", la, lo))
        }
        return out
    }
}
