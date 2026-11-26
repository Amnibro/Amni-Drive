package com.amni.drive.wire
import org.junit.Assert.assertTrue
import org.junit.Test
class CarDensityTest {
    private fun dpOf(px: Int, dpi: Int) = px * 160f / dpi
    @Test
    fun kiaWideDashIsNotMicro() {
        val dpi = AmniProjectionEngine.carDensityDpi(1920, 1080, 0, 360)
        assertTrue("height dp ${dpOf(720, dpi)}", dpOf(720, dpi) >= 300f)
        assertTrue("width dp ${dpOf(1920, dpi)}", dpOf(1920, dpi) >= 720f)
    }
    @Test
    fun classic800x480KeepsUsableWidth() {
        val dpi = AmniProjectionEngine.carDensityDpi(800, 480, 0, 0)
        assertTrue(dpi >= 160)
        assertTrue(dpOf(480, dpi) >= 300f)
    }
    @Test
    fun hd720pAndQhdScaleUp() {
        val hd = AmniProjectionEngine.carDensityDpi(1280, 720, 0, 0)
        val qhd = AmniProjectionEngine.carDensityDpi(2560, 1440, 0, 0)
        assertTrue(qhd > hd)
        assertTrue(dpOf(720, hd) in 300f..420f)
        assertTrue(dpOf(1440, qhd) in 300f..500f)
    }
}
