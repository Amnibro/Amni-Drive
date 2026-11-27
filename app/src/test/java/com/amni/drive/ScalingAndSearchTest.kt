package com.amni.drive
import com.amni.drive.ui.WebAppEntry
import com.amni.drive.wire.AmniProjectionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class ScalingAndSearchTest {
    @Test
    fun carDpi_kiaUltrawide1920x1080With360Margin_givesBalancedViewport() {
        val dpi = AmniProjectionEngine.computeCarDpi(1920, 1080, 0, 360)
        val effHDp = 720f * 160f / dpi
        val effWDp = 1920f * 160f / dpi
        assertTrue("Expected ~414dp height, got $effHDp (dpi=$dpi)", effHDp in 405f..425f)
        assertTrue("Expected >1000dp width, got $effWDp", effWDp > 1050f)
    }
    @Test
    fun carDpi_honda1280x720_givesBalancedViewport() {
        val dpi = AmniProjectionEngine.computeCarDpi(1280, 720, 0, 0)
        val effHDp = 720f * 160f / dpi
        val effWDp = 1280f * 160f / dpi
        assertTrue("Expected ~414dp height, got $effHDp", effHDp in 405f..425f)
        assertTrue("Expected >=720dp width, got $effWDp", effWDp >= 720f)
    }
    @Test
    fun carDpi_wvga800x480_neverCrushesToMicro() {
        val dpi = AmniProjectionEngine.computeCarDpi(800, 480, 0, 0)
        val effHDp = 480f * 160f / dpi
        val effWDp = 800f * 160f / dpi
        assertTrue("Expected >=405dp height, got $effHDp", effHDp >= 405f)
        assertTrue("Expected >=680dp width, got $effWDp", effWDp >= 680f)
    }
    @Test
    fun webSearchUrl_encodesAndRoutesAllMajorApps() {
        val yt = WebAppEntry.CATALOG.first { it.id == "youtube" }
        val sp = WebAppEntry.CATALOG.first { it.id == "spotify" }
        val gg = WebAppEntry.CATALOG.first { it.id == "google" }
        assertEquals("https://m.youtube.com/results?search_query=lofi+beats", yt.buildSearchUrl("lofi beats"))
        assertEquals("https://open.spotify.com/search/daft+punk", sp.buildSearchUrl("daft punk"))
        assertEquals("https://www.google.com/search?q=coffee+near+me", gg.buildSearchUrl("coffee near me"))
    }
}
