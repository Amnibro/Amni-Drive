package com.amni.drive.helium
import android.content.Context
import java.nio.ByteBuffer
object HeliumCore {
    val available: Boolean = runCatching {
        System.loadLibrary("amni_drive_he")
        true
    }.getOrDefault(false)
    @Volatile private var fontLoaded = false
    fun ensureFontLoaded(ctx: Context): Boolean {
        if (!available) return false
        if (fontLoaded) return true
        return runCatching {
            val bytes = ctx.assets.open("fonts/AtkinsonHyperlegible-Bold.ttf").use { it.readBytes() }
            val ok = nativeLoadFont(bytes) != 0
            if (ok) fontLoaded = true
            ok
        }.getOrDefault(false)
    }
    fun resolveGate(driveState: Int, gateEnabled: Boolean, passengerConfirmed: Boolean, category: Int): Int? =
        if (!available) null else runCatching {
            nativeGate(driveState, if (gateEnabled) 1 else 0, if (passengerConfirmed) 1 else 0, category)
        }.getOrNull()
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double? =
        if (!available) null else runCatching { nativeHaversine(lat1, lon1, lat2, lon2) }.getOrNull()
    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float? =
        if (!available) null else runCatching { nativeBearing(lat1, lon1, lat2, lon2) }.getOrNull()
    fun paletteColor(presetIdx: Int, slot: Int, customAccent: Int = 0, highContrast: Boolean = false): Int? =
        if (!available) null else runCatching { nativePaletteColor(presetIdx, slot, customAccent, if (highContrast) 1 else 0) }.getOrNull()
    fun typeMetric(screenW: Float, screenH: Float, userScale: Float, slot: Int): Float? =
        if (!available) null else runCatching { nativeTypeMetric(screenW, screenH, userScale, slot) }.getOrNull()
    fun validateCockpit(screenW: Float, screenH: Float, userScale: Float, layoutId: Int, dockMode: Int, swap: Boolean): Int? =
        if (!available) null else runCatching { nativeCockpitValid(screenW, screenH, userScale, layoutId, dockMode, if (swap) 1 else 0) }.getOrNull()
    fun scrubText(raw: String): String? =
        if (!available) null else runCatching {
            val out = nativeScrub(raw.toByteArray(Charsets.UTF_8)) ?: return null
            String(out, Charsets.UTF_8)
        }.getOrNull()
    fun renderCockpitRgba(outDirect: ByteBuffer, w: Int, h: Int, presetIdx: Int, layoutId: Int, dockMode: Int, speedMph: Int, tempF: Int, activeBtn: Int, rollDeg: Float = 0f): Int =
        if (!available || !outDirect.isDirect) 0 else runCatching {
            nativeRenderRgba(outDirect, w, h, presetIdx, layoutId, dockMode, speedMph, tempF, activeBtn, rollDeg)
        }.getOrDefault(0)
    data class DrumStepResult(val angleDeg: Float, val velDeg: Float, val activeIdx: Int)
    fun drumStep(angleDeg: Float, velDeg: Float, dt: Float, dragging: Boolean): DrumStepResult {
        if (available) {
            val out = FloatArray(2)
            val idx = runCatching { nativeDrumStep(angleDeg, velDeg, dt, if (dragging) 1 else 0, out) }.getOrNull()
            if (idx != null) return DrumStepResult(out[0], out[1], idx)
        }
        if (dragging) return DrumStepResult(angleDeg, velDeg, (((Math.round(angleDeg / 46f)) % 4) + 4) % 4)
        val clampedDt = dt.coerceIn(0.001f, 0.05f)
        val target = Math.round(angleDeg / 46f) * 46f
        val acc = (target - angleDeg) * 140f - velDeg * 21f
        var v = velDeg + acc * clampedDt
        var a = angleDeg + v * clampedDt
        if (kotlin.math.abs(target - a) < 0.08f && kotlin.math.abs(v) < 0.4f) { a = target; v = 0f }
        val idx = (((Math.round(a / 46f)) % 4) + 4) % 4
        return DrumStepResult(a, v, idx)
    }
    @JvmStatic private external fun nativeGate(ds: Int, on: Int, pass: Int, cat: Int): Int
    @JvmStatic private external fun nativeHaversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double
    @JvmStatic private external fun nativeBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float
    @JvmStatic private external fun nativePaletteColor(idx: Int, slot: Int, customAcc: Int, hc: Int): Int
    @JvmStatic private external fun nativeTypeMetric(w: Float, h: Float, s: Float, slot: Int): Float
    @JvmStatic private external fun nativeCockpitValid(w: Float, h: Float, s: Float, lid: Int, dock: Int, swap: Int): Int
    @JvmStatic private external fun nativeLoadFont(fontBytes: ByteArray): Int
    @JvmStatic private external fun nativeScrub(input: ByteArray): ByteArray?
    @JvmStatic private external fun nativeRenderRgba(outDirect: ByteBuffer, w: Int, h: Int, presetIdx: Int, layoutId: Int, dockMode: Int, speedMph: Int, tempF: Int, activeBtn: Int, rollDeg: Float): Int
    @JvmStatic private external fun nativeDrumStep(angleDeg: Float, velDeg: Float, dt: Float, dragging: Int, outState: FloatArray): Int
}
