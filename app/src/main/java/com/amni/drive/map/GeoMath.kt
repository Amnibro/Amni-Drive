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
}
