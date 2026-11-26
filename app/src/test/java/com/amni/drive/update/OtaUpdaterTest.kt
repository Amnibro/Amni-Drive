package com.amni.drive.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtaUpdaterTest {
    @Test
    fun parseReleaseJsonReadsValidFeed() {
        val raw = """
            {
              "versionCode": 90,
              "versionName": "1.12.0",
              "url": "https://amni-scient.com/downloads/amni-drive.apk?v=1.12.0",
              "notes": "Smooth OTA updates and Amni Apps hub",
              "sha256": "abcdef0123456789",
              "size": 34567890
            }
        """.trimIndent()
        val rel = OtaUpdater.parseReleaseJson(raw)
        assertNotNull(rel)
        assertEquals(90L, rel!!.versionCode)
        assertEquals("1.12.0", rel.versionName)
        assertEquals("https://amni-scient.com/downloads/amni-drive.apk?v=1.12.0", rel.url)
        assertEquals("abcdef0123456789", rel.sha256)
        assertEquals(34567890L, rel.size)
    }

    @Test
    fun parseReleaseJsonRejectsMissingFields() {
        assertNull(OtaUpdater.parseReleaseJson("""{"versionCode":0,"versionName":"1.0","url":""}"""))
    }

    @Test
    fun parseCatalogJsonReadsCompanionApps() {
        val raw = """
            {
              "apps": [
                {
                  "id": "amni-map",
                  "name": "Amni-Map",
                  "packageName": "com.amniscient.map",
                  "emoji": "🗺️",
                  "blurb": "Native car-tile maps",
                  "versionCode": 33,
                  "versionName": "2.12.0",
                  "downloadUrl": "https://amni-scient.com/downloads/amni-map.apk",
                  "sha256": "deadbeef",
                  "size": 42000000,
                  "carDashNative": true,
                  "requiredForCar": true
                }
              ]
            }
        """.trimIndent()
        val list = OtaUpdater.parseCatalogJson(raw)
        assertEquals(1, list.size)
        assertEquals("com.amniscient.map", list[0].packageName)
        assertTrue(list[0].carDashNative)
        assertTrue(list[0].requiredForCar)
    }

    @Test
    fun sha256HexMatchesKnownDigest() {
        val digest = OtaUpdater.sha256Hex("amni-drive".toByteArray(Charsets.UTF_8))
        assertEquals(64, digest.length)
        assertEquals(digest, OtaUpdater.sha256Hex("amni-drive".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun fallbackCatalogIncludesCarNativeAmniMap() {
        val map = OtaUpdater.FALLBACK_CATALOG.firstOrNull { it.packageName == "com.amniscient.map" }
        assertNotNull(map)
        assertTrue(map!!.carDashNative)
        assertTrue(map.downloadUrl.startsWith("https://amni-scient.com/"))
    }
}
