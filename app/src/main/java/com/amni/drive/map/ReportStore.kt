package com.amni.drive.map
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
data class Report(val type: String, val emoji: String, val lat: Double, val lon: Double, val at: Long)
class ReportStore(context: Context) {
    private val file = File(context.filesDir, "reports.json")
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _reports = MutableStateFlow<List<Report>>(emptyList())
    val reports: StateFlow<List<Report>> = _reports
    init { scope.launch { _reports.value = load() } }
    fun add(type: String, emoji: String, lat: Double, lon: Double) {
        val now = System.currentTimeMillis()
        val next = (_reports.value + Report(type, emoji, lat, lon, now)).filter { now - it.at < 7200000L }
        _reports.value = next
        scope.launch { runCatching { file.writeText(JSONArray(next.map { r -> JSONObject().put("t", r.type).put("e", r.emoji).put("la", r.lat).put("lo", r.lon).put("at", r.at) }).toString()) } }
    }
    private fun load(): List<Report> = runCatching {
        val now = System.currentTimeMillis()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { arr.getJSONObject(it) }.map { Report(it.getString("t"), it.getString("e"), it.getDouble("la"), it.getDouble("lo"), it.getLong("at")) }.filter { now - it.at < 7200000L }
    }.getOrDefault(emptyList())
}
