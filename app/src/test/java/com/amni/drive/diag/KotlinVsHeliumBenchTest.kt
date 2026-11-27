package com.amni.drive.diag
import com.amni.drive.gate.AppCategory
import com.amni.drive.gate.DriveState
import com.amni.drive.gate.GateDecision
import com.amni.drive.gate.GateInput
import com.amni.drive.gate.GateManager
import com.amni.drive.gate.GatePolicy
import com.amni.drive.map.GeoMath
import com.amni.drive.wire.AaPb
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
class KotlinVsHeliumBenchTest {
    private fun chLum(c: Int): Double {
        val s = (c and 0xFF) / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }
    private fun lum(col: Int): Double = 0.2126 * chLum(col ushr 16) + 0.7152 * chLum(col ushr 8) + 0.0722 * chLum(col)
    private fun contrast(a: Int, b: Int): Double {
        val la = lum(a); val lb = lum(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
    private fun legible(fg: Int, bg: Int, minR: Double): Int {
        if (contrast(fg, bg) >= minR) return fg
        val pole = if (lum(bg) < 0.25) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        for (step in 1..16) {
            val t = step / 16.0
            val r = (((fg ushr 16) and 0xFF) * (1 - t) + ((pole ushr 16) and 0xFF) * t).toInt()
            val g = (((fg ushr 8) and 0xFF) * (1 - t) + ((pole ushr 8) and 0xFF) * t).toInt()
            val b = ((fg and 0xFF) * (1 - t) + (pole and 0xFF) * t).toInt()
            val cand = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            if (contrast(cand, bg) >= minR) return cand
        }
        return pole
    }
    private fun decodePbTwoFields(data: ByteArray): Long {
        var off = 0
        var acc = 0L
        while (off < data.size) {
            var tag = 0L; var sh = 0
            while (off < data.size) {
                val b = data[off++].toInt() and 0xFF
                tag = tag or ((b and 0x7F).toLong() shl sh)
                if ((b and 0x80) == 0) break
                sh += 7
            }
            val wt = (tag and 7L).toInt()
            if (wt == 0) {
                var v = 0L; var vs = 0
                while (off < data.size) {
                    val b = data[off++].toInt() and 0xFF
                    v = v or ((b and 0x7F).toLong() shl vs)
                    if ((b and 0x80) == 0) break
                    vs += 7
                }
                acc += v
            } else if (wt == 2) {
                var len = 0; var ls = 0
                while (off < data.size) {
                    val b = data[off++].toInt() and 0xFF
                    len = len or ((b and 0x7F) shl ls)
                    if ((b and 0x80) == 0) break
                    ls += 7
                }
                acc += len
                off += len
            } else break
        }
        return acc
    }
    private fun kotlinLayoutPods(w: Float, h: Float, layoutId: Int): Int {
        val dockW = max(74f, min(w * 0.12f, 92f))
        val sw = w - dockW - 36f
        val sh = h - 28f
        val d = min(sh * 0.97f, sw * 0.56f)
        return when (layoutId) {
            0 -> if (d > 0f) 4 else 2
            1 -> if (d > 0f) 5 else 2
            4 -> 4
            10 -> 3
            else -> 3
        }
    }
    @Test
    fun runKotlinBenchmarks() {
        val sampleLine = "2026-11-27 user anthony@amni-scient.com token=sk_live_987654321 ip 192.168.1.42 loc 40.7128, -74.0060 call 555-867-5309"
        repeat(200) { LogScrubber.scrub(sampleLine) }
        var t0 = System.nanoTime()
        var sum = 0L
        repeat(10_000) { sum += LogScrubber.scrub(sampleLine).length }
        val scrubMs = (System.nanoTime() - t0) / 1_000_000.0
        println("KT_BENCH scrub 10000 ${"%.2f".format(scrubMs)} $sum")
        val cats = AppCategory.entries.toTypedArray()
        val pol = GatePolicy.GATED
        repeat(10_000) { i -> GeoMath.haversineM(40.7128, -74.0060, 40.7580, -73.9855); GateManager.decide(GateInput(cats[i % cats.size], pol, DriveState.MOVING, true, false)) }
        t0 = System.nanoTime()
        sum = 0L
        for (i in 0 until 1_000_000) {
            val d = (i and 1023) * 0.0001
            val dist = GeoMath.haversineM(40.7128 + d, -74.0060, 40.7580, -73.9855 - d)
            val dec = if (GateManager.decide(GateInput(cats[i % cats.size], pol, DriveState.MOVING, true, false)).allow) 0L else 1L
            sum += dist.toLong() + dec
        }
        val geoMs = (System.nanoTime() - t0) / 1_000_000.0
        println("KT_BENCH geo_gate 1000000 ${"%.2f".format(geoMs)} $sum")
        val pbPayload = AaPb.cat(AaPb.u32(1, 300), AaPb.str(2, "amni-drive-audio"))
        val rawHdr = byteArrayOf(3, 1, 0, 16, 0, 0, 0, 32)
        repeat(10_000) { decodePbTwoFields(pbPayload) }
        t0 = System.nanoTime()
        sum = 0L
        repeat(500_000) {
            val hdrSize = if ((rawHdr[1].toInt() and 3) == 1) 8L else 4L
            sum += hdrSize + decodePbTwoFields(pbPayload)
        }
        val aapMs = (System.nanoTime() - t0) / 1_000_000.0
        println("KT_BENCH aap_codec 500000 ${"%.2f".format(aapMs)} $sum")
        repeat(2_000) { i -> legible(0xFF00FF9D.toInt(), 0xFF181818.toInt(), 5.2); kotlinLayoutPods(1280f, 720f, i % 11) }
        t0 = System.nanoTime()
        sum = 0L
        for (i in 0 until 100_000) {
            val acc = legible(0xFF00FF9D.toInt(), 0xFF181818.toInt(), 5.2)
            val soft = legible(0xFFC5D0DC.toInt(), 0xFF181818.toInt(), 7.0)
            val dim = legible(0xFF93A3B5.toInt(), 0xFF181818.toInt(), 4.5)
            val pods = kotlinLayoutPods(1280f, 720f, i % 11)
            sum += (acc and 0xFF) + (soft and 0xFF) + (dim and 0xFF) + pods
        }
        val layMs = (System.nanoTime() - t0) / 1_000_000.0
        println("KT_BENCH layout_aaa 100000 ${"%.2f".format(layMs)} $sum")
    }
}
