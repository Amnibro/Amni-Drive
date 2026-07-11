package com.amni.drive.weather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
enum class Wx { CLEAR, CLOUDS, FOG, RAIN, THUNDER, SNOW, HAIL, NONE }
data class WeatherNow(val tempF: Int, val code: Int, val windMph: Int, val humidity: Int) {
    val wx: Wx get() = when (code) {
        0, 1 -> Wx.CLEAR
        2, 3 -> Wx.CLOUDS
        45, 48 -> Wx.FOG
        in 51..67, in 80..82 -> Wx.RAIN
        in 71..77, 85, 86 -> Wx.SNOW
        96, 99 -> Wx.HAIL
        95 -> Wx.THUNDER
        else -> Wx.CLOUDS
    }
    val emoji: String get() = when (wx) { Wx.CLEAR -> "☀️"; Wx.CLOUDS -> "⛅"; Wx.FOG -> "🌫️"; Wx.RAIN -> "🌧️"; Wx.THUNDER -> "⛈️"; Wx.SNOW -> "❄️"; Wx.HAIL -> "🌨️"; Wx.NONE -> "🌡️" }
    val label: String get() = when (wx) { Wx.CLEAR -> "Clear skies"; Wx.CLOUDS -> "Cloudy"; Wx.FOG -> "Fog"; Wx.RAIN -> "Rain"; Wx.THUNDER -> "Thunderstorm"; Wx.SNOW -> "Snow"; Wx.HAIL -> "Hail"; Wx.NONE -> "—" }
}
object WeatherService {
    suspend fun fetch(lat: Double, lon: Double): WeatherNow? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,wind_speed_10m,relative_humidity_2m&temperature_unit=fahrenheit&wind_speed_unit=mph"
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            val cur = JSONObject(c.inputStream.use { it.bufferedReader().readText() }).getJSONObject("current")
            WeatherNow(cur.getDouble("temperature_2m").toInt(), cur.getInt("weather_code"), cur.getDouble("wind_speed_10m").toInt(), cur.getInt("relative_humidity_2m"))
        }.getOrNull()
    }
}
