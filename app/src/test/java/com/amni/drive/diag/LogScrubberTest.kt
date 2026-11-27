package com.amni.drive.diag
import com.amni.drive.map.MapCamera
import com.amni.drive.ui.AmniMapPrefs
import com.amni.drive.ui.AppLauncherShape
import com.amni.drive.ui.TileLayout
import com.amni.drive.update.OtaUpdater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class LogScrubberTest {
    @Test
    fun scrubsPersonalDataWhilePreservingSupportEmailAndVersions() {
        val raw = listOf(
            "Amni-Drive v1.14.0 crash send to anthony@amni-scient.com from john.doe@gmail.com",
            "GPS fix lat=30.2672, lon=-97.7431 pair 30.267200, -97.743100 latE7=302672000",
            "Voice Searching “123 Main St Austin” and Routing to 456 Oak Ave",
            "Notif Message from Alice: meet me at home",
            "Net q=secret+addr token=abc123xyz Wi-Fi: MyHomeNet · bssid AA:BB:CC:DD:EE:FF ip 192.168.1.45",
            "Call (512) 555-0199 file /storage/emulated/0/Download/private_note.txt"
        ).joinToString("\n")
        val out = LogScrubber.scrub(raw)
        assertTrue(out.contains("anthony@amni-scient.com"))
        assertTrue(out.contains("v1.14.0"))
        assertFalse(out.contains("john.doe@gmail.com"))
        assertTrue(out.contains("<EMAIL>"))
        assertFalse(out.contains("30.2672"))
        assertFalse(out.contains("-97.7431"))
        assertFalse(out.contains("302672000"))
        assertFalse(out.contains("123 Main St"))
        assertFalse(out.contains("456 Oak Ave"))
        assertFalse(out.contains("Alice"))
        assertFalse(out.contains("secret+addr"))
        assertFalse(out.contains("abc123xyz"))
        assertFalse(out.contains("MyHomeNet"))
        assertFalse(out.contains("AA:BB:CC:DD:EE:FF"))
        assertFalse(out.contains("192.168.1.45"))
        assertFalse(out.contains("555-0199"))
        assertFalse(out.contains("private_note.txt"))
    }
    @Test
    fun defaultsMapCameraAndAmniMapPrefsToPerspectiveHeadingUpAndThemeSync() {
        val cam = MapCamera(30.2672, -97.7431)
        assertFalse(cam.northUp)
        assertTrue(cam.tilt)
        val prefs = AmniMapPrefs()
        assertTrue(prefs.perspective3d)
        assertTrue(prefs.headingUp)
        assertTrue(prefs.buildings3d)
        assertTrue(prefs.trees3d)
        assertTrue(prefs.bridges3d)
        assertEquals("light", prefs.effectiveTheme(true))
        assertEquals("dark", prefs.effectiveTheme(false))
        assertEquals("sunset", prefs.copy(themeMode = "sunset").effectiveTheme(true))
    }
    @Test
    fun mapsBespokeCockpitShapesAndBundledAmniMapUpdateBanner() {
        assertEquals("circle", TileLayout.TWIN_CHRONO.mapShape)
        assertEquals("circle", TileLayout.SOLAR_ORBIT.mapShape)
        assertEquals("circle", TileLayout.ORB_CENTER.mapShape)
        assertEquals("hex", TileLayout.HEX_COCKPIT.mapShape)
        assertEquals("capsule", TileLayout.CAPSULE_DECK.mapShape)
        assertEquals(AppLauncherShape.ORBITAL, AppLauncherShape.AUTO.resolve(TileLayout.TWIN_CHRONO))
        assertEquals(AppLauncherShape.HONEYCOMB, AppLauncherShape.AUTO.resolve(TileLayout.HEX_COCKPIT))
        assertEquals(AppLauncherShape.CAPSULE, AppLauncherShape.AUTO.resolve(TileLayout.CAPSULE_DECK))
        val bundled = OtaUpdater.BundledApkInfo("com.amniscient.map", 37L, "2.14.0", 42770000L, "abc")
        val mapApp = OtaUpdater.FALLBACK_CATALOG.first { it.id == "amni-map" }
        val missingState = OtaUpdater.OtaState(
            bundledMap = bundled,
            apps = listOf(OtaUpdater.CompanionAppStatus(mapApp, installed = false, installedVersionCode = 0L, installedVersionName = null, prebundled = true))
        )
        assertTrue(missingState.bundledMapMissing)
        assertTrue(missingState.hasActionableBanner)
    }
}
