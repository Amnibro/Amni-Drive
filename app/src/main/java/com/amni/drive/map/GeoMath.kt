package com.amni.drive.map
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan
object GeoMath {
    fun lonToTileX(lon: Double, z: Int): Double = (lon + 180.0) / 360.0 * 2.0.pow(z)
    fun latToTileY(lat: Double, z: Int): Double {
        val r = lat * PI / 180.0
        return (1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * 2.0.pow(z)
    }
    fun tileXToLon(x: Double, z: Int): Double = x / 2.0.pow(z) * 360.0 - 180.0
    fun tileYToLat(y: Double, z: Int): Double = atan(sinh(PI * (1.0 - 2.0 * y / 2.0.pow(z)))) * 180.0 / PI
    fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        com.amni.drive.helium.HeliumCore.haversineMeters(lat1, lon1, lat2, lon2)?.let { return it }
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * kotlin.math.sin(dLon / 2).let { it * it }
        return 2.0 * 6371000.0 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt((1.0 - a).coerceAtLeast(0.0)))
    }
}
