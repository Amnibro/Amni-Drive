package com.amni.drive.wire

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AaPostTlsTest {

    private fun videoConfigBytes(resType: Int, fpsType: Int = 1, wMargin: Int = 0, hMargin: Int = 0, density: Int = 240): ByteArray {
        return AaPb.cat(
            AaPb.u32(1, resType),
            AaPb.u32(2, fpsType),
            AaPb.u32(3, wMargin),
            AaPb.u32(4, hMargin),
            AaPb.u32(5, density)
        )
    }

    @Test
    fun parseChannelsExtractsAllVideoConfigsAndSelectsBestUnder1080p() {
        val mediaSink = AaPb.cat(
            AaPb.u32(1, 3), // H264_BP
            AaPb.emb(4, videoConfigBytes(1, density = 160)),           // idx 0: 800x480
            AaPb.emb(4, videoConfigBytes(2, density = 200)),           // idx 1: 1280x720
            AaPb.emb(4, videoConfigBytes(3, hMargin = 360, density = 240)), // idx 2: 1920x1080
            AaPb.emb(4, videoConfigBytes(5, density = 320))            // idx 3: 3840x2160 (4K)
        )
        val service = AaPb.cat(
            AaPb.u32(1, 1),
            AaPb.emb(3, mediaSink)
        )
        val sdResp = AaPb.emb(1, service)
        val chans = AaPostTls.parseChannels(sdResp)
        assertEquals(1, chans.size)
        val av = chans.first()
        assertEquals("av", av.kind)
        assertEquals(4, av.videoSpecs.size)

        // When HU offers indices [0, 1, 2, 3], selectVideoConfig picks idx 2 (1920x1080, highest <= 1080p)
        val (chosenIdx, chosenSpec) = AaPostTls.selectVideoConfig(av.videoSpecs, listOf(0, 1, 2, 3))
        assertEquals(2, chosenIdx)
        assertEquals(1920, chosenSpec.width)
        assertEquals(1080, chosenSpec.height)
        assertEquals(360, chosenSpec.heightMargin)

        // When HU only offers [0, 1], picks idx 1 (1280x720)
        val (idx720, spec720) = AaPostTls.selectVideoConfig(av.videoSpecs, listOf(0, 1))
        assertEquals(1, idx720)
        assertEquals(1280, spec720.width)
        assertEquals(720, spec720.height)
    }

    @Test
    fun parseKeyEventsAndRelativeEvents() {
        val keyEntry = AaPb.cat(AaPb.u32(1, 23), AaPb.bool(2, true)) // DPAD_CENTER down
        val keyEvent = AaPb.emb(1, keyEntry)
        val relEntry = AaPb.cat(AaPb.u32(1, 261), AaPb.u32(2, 2))    // NAVIGATE_NEXT +2
        val relEvent = AaPb.emb(1, relEntry)
        val report = AaPb.cat(
            AaPb.emb(4, keyEvent),
            AaPb.emb(6, relEvent)
        )
        val keys = AaPostTls.parseKeyEvents(report)
        assertEquals(1, keys.size)
        assertEquals(23, keys[0].keycode)
        assertTrue(keys[0].down)

        val rels = AaPostTls.parseRelativeEvents(report)
        assertEquals(1, rels.size)
        assertEquals(261, rels[0].keycode)
        assertEquals(2, rels[0].delta)
    }

    @Test
    fun cappedVersionReplyHandlesLegacyAndModernHeadUnits() {
        assertEquals(1 to 5, AaAapVersion.cappedVersionReply(1, 5))
        assertEquals(1 to 7, AaAapVersion.cappedVersionReply(1, 7))
        assertEquals(6 to 2, AaAapVersion.cappedVersionReply(1, 8))
        assertEquals(6 to 2, AaAapVersion.cappedVersionReply(6, 5))
    }

    @Test
    fun certAuthRefusalTriggersFallbackFlagAndClearsOnZero() {
        AaCertStore.noteAuthRefusal(0)
        assertFalse(AaCertStore.preferFallbackThisBoot)
        assertEquals(0, AaCertStore.lastAuthStatus)

        AaCertStore.noteAuthRefusal(-3)
        assertTrue(AaCertStore.preferFallbackThisBoot)
        assertEquals(-3, AaCertStore.lastAuthStatus)

        AaCertStore.noteAuthRefusal(0)
        assertFalse(AaCertStore.preferFallbackThisBoot)
        assertEquals(0, AaCertStore.lastAuthStatus)
    }
}
