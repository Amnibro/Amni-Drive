package com.amni.drive.map

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID

/**
 * Local user-added places ("Add missing place") with optional free
 * OpenStreetMap Note submission so missing restaurants/POIs work immediately
 * in Amni-Drive search and can also be contributed upstream to OSM.
 */
object CustomPlaces {
    data class Entry(
        val id: String,
        val name: String,
        val category: String,
        val lat: Double,
        val lon: Double,
        val createdAt: Long,
        val osmNoteId: Long = 0L
    ) {
        fun toPlace(): Place = Place(
            name = name,
            detail = listOf(category, "Added by you").filter { it.isNotBlank() }.joinToString(" · "),
            lat = lat,
            lon = lon
        )
    }

    private val lock = Any()
    private var appCtx: Context? = null
    private var cache: MutableList<Entry>? = null

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
    }

    private fun file(ctx: Context): File = File(ctx.filesDir, "custom_places.json")

    fun all(ctx: Context? = appCtx): List<Entry> = synchronized(lock) {
        if (ctx == null) return emptyList()
        cache?.let { return it.toList() }
        val list = mutableListOf<Entry>()
        val f = file(ctx)
        if (f.exists()) {
            runCatching {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        Entry(
                            id = o.optString("id", UUID.randomUUID().toString()),
                            name = o.optString("name"),
                            category = o.optString("category"),
                            lat = o.getDouble("lat"),
                            lon = o.getDouble("lon"),
                            createdAt = o.optLong("createdAt", 0L),
                            osmNoteId = o.optLong("osmNoteId", 0L)
                        )
                    )
                }
            }
        }
        cache = list
        list.toList()
    }

    private fun save(ctx: Context, list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("name", e.name)
                    .put("category", e.category)
                    .put("lat", e.lat)
                    .put("lon", e.lon)
                    .put("createdAt", e.createdAt)
                    .put("osmNoteId", e.osmNoteId)
            )
        }
        val f = file(ctx)
        val tmp = File(f.parentFile, "${f.name}.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(f)) {
            f.writeText(arr.toString())
            tmp.delete()
        }
    }

    fun search(query: String, lat: Double?, lon: Double?, ctx: Context? = appCtx): List<Place> {
        val q = query.trim().lowercase(Locale.US)
        if (q.isEmpty()) return emptyList()
        return all(ctx)
            .filter { e ->
                e.name.lowercase(Locale.US).contains(q) ||
                    e.category.lowercase(Locale.US).contains(q)
            }
            .sortedBy { e ->
                if (lat != null && lon != null) GeoMath.haversineM(lat, lon, e.lat, e.lon) else 0.0
            }
            .map { it.toPlace() }
    }

    suspend fun add(
        ctx: Context,
        name: String,
        category: String,
        lat: Double,
        lon: Double,
        submitToOsm: Boolean = true
    ): Entry = withContext(Dispatchers.IO) {
        init(ctx)
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Place name is required" }
        val cleanCat = category.trim().ifEmpty { "Place" }
        val noteId = if (submitToOsm) submitOsmNote(cleanName, cleanCat, lat, lon) else 0L
        val entry = Entry(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            category = cleanCat,
            lat = lat,
            lon = lon,
            createdAt = System.currentTimeMillis(),
            osmNoteId = noteId
        )
        synchronized(lock) {
            val list = all(ctx).toMutableList()
            list.add(0, entry)
            cache = list
            save(ctx, list)
        }
        entry
    }

    private fun submitOsmNote(name: String, category: String, lat: Double, lon: Double): Long {
        return runCatching {
            val text = "Missing place reported via Amni-Drive: $name ($category)"
            val body = "lat=${String.format(Locale.US, "%.6f", lat)}" +
                "&lon=${String.format(Locale.US, "%.6f", lon)}" +
                "&text=${URLEncoder.encode(text, "UTF-8")}"
            val conn = (URL("https://api.openstreetmap.org/api/0.6/notes.json").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "AmniDrive/1.10 (https://amni-scient.com)")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            try {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode in 200..299) {
                    val raw = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    JSONObject(raw).optJSONObject("properties")?.optLong("id", 0L) ?: 0L
                } else 0L
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(0L)
    }
}
