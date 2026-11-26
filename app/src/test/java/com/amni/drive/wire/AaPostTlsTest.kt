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
        assertEquals(1 to 7, AaAapVersion.cappedVersionReply(1, 8))
        assertEquals(1 to 7, AaAapVersion.cappedVersionReply(4, 1))
        assertEquals(1 to 7, AaAapVersion.cappedVersionReply(6, 5))
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

    @Test
    fun selectVideoConfigSupportsPortraitDisplaysLikeSubaruAndRam() {
        val specs = listOf(
            AaPostTls.VideoSpec(width = 800, height = 480, dpi = 160),
            AaPostTls.VideoSpec(width = 720, height = 1280, dpi = 240),
            AaPostTls.VideoSpec(width = 1080, height = 1920, dpi = 320),
            AaPostTls.VideoSpec(width = 2160, height = 3840, dpi = 420)
        )
        val (idx, chosen) = AaPostTls.selectVideoConfig(specs, listOf(0, 1, 2, 3))
        assertEquals(2, idx)
        assertEquals(1080, chosen.width)
        assertEquals(1920, chosen.height)
    }

    @Test
    fun parseChannelsDistinguishesVideoMediaAudioSpeechAudioSensorAndBt() {
        val videoSink = AaPb.cat(
            AaPb.u32(1, AaPostTls.CODEC_H264_BP),
            AaPb.emb(4, videoConfigBytes(7, density = 320)) // 1080x1920 portrait
        )
        val mediaAudioSink = AaPb.cat(
            AaPb.u32(1, AaPostTls.CODEC_PCM),
            AaPb.u32(2, AaPostTls.AUDIO_STREAM_MEDIA),
            AaPb.emb(3, AaPb.cat(AaPb.u32(1, 48_000), AaPb.u32(2, 16), AaPb.u32(3, 2)))
        )
        val speechAudioSink = AaPb.cat(
            AaPb.u32(1, AaPostTls.CODEC_PCM),
            AaPb.u32(2, AaPostTls.AUDIO_STREAM_SPEECH),
            AaPb.emb(3, AaPb.cat(AaPb.u32(1, 16_000), AaPb.u32(2, 16), AaPb.u32(3, 1)))
        )
        val sensorSvc = AaPb.cat(
            AaPb.emb(1, AaPb.u32(1, AaPostTls.SENSOR_DRIVING_STATUS)),
            AaPb.emb(1, AaPb.u32(1, AaPostTls.SENSOR_NIGHT_MODE)),
            AaPb.emb(1, AaPb.u32(1, AaPostTls.SENSOR_PARKING_BRAKE))
        )
        val btSvc = AaPb.cat(
            AaPb.str(1, "AA:BB:CC:DD:EE:FF"),
            AaPb.u32(2, 2)
        )
        val sdResp = AaPb.cat(
            AaPb.emb(1, AaPb.cat(AaPb.u32(1, 1), AaPb.emb(3, videoSink))),
            AaPb.emb(1, AaPb.cat(AaPb.u32(1, 3), AaPb.emb(3, mediaAudioSink))),
            AaPb.emb(1, AaPb.cat(AaPb.u32(1, 4), AaPb.emb(3, speechAudioSink))),
            AaPb.emb(1, AaPb.cat(AaPb.u32(1, 5), AaPb.emb(2, sensorSvc))),
            AaPb.emb(1, AaPb.cat(AaPb.u32(1, 6), AaPb.emb(6, btSvc)))
        )
        val chans = AaPostTls.parseChannels(sdResp)
        assertEquals(5, chans.size)
        val v = chans.first { it.isVideoSink }
        assertEquals(1, v.id)
        assertEquals(1080, v.videoSpec?.width)
        assertEquals(1920, v.videoSpec?.height)

        val ma = chans.first { it.isAudioSink && it.audioType == AaPostTls.AUDIO_STREAM_MEDIA }
        assertEquals(3, ma.id)
        assertEquals(48_000, ma.audioSpec?.sampleRate)
        assertEquals(2, ma.audioSpec?.channels)

        val sa = chans.first { it.isAudioSink && it.audioType == AaPostTls.AUDIO_STREAM_SPEECH }
        assertEquals(4, sa.id)
        assertEquals(16_000, sa.audioSpec?.sampleRate)

        val s = chans.first { it.kind == "sensor" }
        assertEquals(listOf(AaPostTls.SENSOR_DRIVING_STATUS, AaPostTls.SENSOR_NIGHT_MODE, AaPostTls.SENSOR_PARKING_BRAKE), s.sensorTypes)

        val bt = chans.first { it.kind == "bt" }
        assertEquals("AA:BB:CC:DD:EE:FF", bt.btAddress)
        assertEquals(listOf(2), bt.btPairingMethods)
    }

    @Test
    fun aapRxReassemblerReassemblesExtendedMultiFragmentMessages() {
        val fullPayload = ByteArray(40_000) { idx ->
            when (idx) {
                0 -> 0x00
                1 -> 0x06 // MSG_SERVICE_DISCOVERY_RESPONSE
                else -> (idx and 0xFF).toByte()
            }
        }
        val part1 = fullPayload.copyOfRange(0, 16_000)
        val part2 = fullPayload.copyOfRange(16_000, 32_000)
        val part3 = fullPayload.copyOfRange(32_000, 40_000)

        fun buildFrame(ch: Int, flags: Int, payload: ByteArray, totalLen: Int? = null): ByteArray {
            val hdrLen = if (totalLen != null) 8 else 4
            val out = ByteArray(hdrLen + payload.size)
            out[0] = ch.toByte()
            out[1] = flags.toByte()
            out[2] = ((payload.size ushr 8) and 0xFF).toByte()
            out[3] = (payload.size and 0xFF).toByte()
            if (totalLen != null) {
                out[4] = ((totalLen ushr 24) and 0xFF).toByte()
                out[5] = ((totalLen ushr 16) and 0xFF).toByte()
                out[6] = ((totalLen ushr 8) and 0xFF).toByte()
                out[7] = (totalLen and 0xFF).toByte()
            }
            System.arraycopy(payload, 0, out, hdrLen, payload.size)
            return out
        }

        val f1 = buildFrame(0, AaAapVersion.ENC_ENCRYPTED or AaAapVersion.FRAME_FIRST, part1, totalLen = fullPayload.size)
        val f2 = buildFrame(0, AaAapVersion.ENC_ENCRYPTED or AaAapVersion.FRAME_MIDDLE, part2)
        val f3 = buildFrame(0, AaAapVersion.ENC_ENCRYPTED or AaAapVersion.FRAME_LAST, part3)
        val wire = AaPb.cat(f1, f2, f3)

        val reassembler = AaPostTls.AapRxReassembler(decrypt = { it })
        val received = ArrayList<AaPostTls.ReassembledMsg>()
        val consumed = reassembler.process(wire) { received.add(it) }
        assertEquals(wire.size, consumed)
        assertEquals(1, received.size)
        assertEquals(0, received[0].channel)
        assertEquals(AaPostTls.MSG_SERVICE_DISCOVERY_RESPONSE, received[0].msgId)
        assertEquals(fullPayload.size - 2, received[0].body.size)
        assertEquals(fullPayload[35_000], received[0].body[34_998])
    }

    @Test
    fun parseSensorEventExtractsNightModeBrakeAndDrivingStatus() {
        val ev = AaPb.cat(
            AaPb.emb(AaPostTls.SENSOR_NIGHT_MODE, AaPb.bool(1, true)),
            AaPb.emb(AaPostTls.SENSOR_PARKING_BRAKE, AaPb.bool(1, true)),
            AaPb.emb(AaPostTls.SENSOR_DRIVING_STATUS, AaPb.u32(1, 0))
        )
        val state = AaPostTls.parseSensorEvent(ev, AaPostTls.CarSensorState())
        assertEquals(true, state.nightMode)
        assertEquals(true, state.parkingBrake)
        assertEquals(0, state.drivingStatus)
    }

    @Test
    fun audioStreamerSilenceDetectorDistinguishesSilenceFromSignal() {
        val silent = ByteArray(128)
        assertFalse(AaAudioStreamer.hasAudibleSignal(silent))
        val loud = ByteArray(128)
        loud[16] = 0x00
        loud[17] = 0x08 // +2048 little-endian sample
        assertTrue(AaAudioStreamer.hasAudibleSignal(loud))
    }

    @Test
    fun parseMediaAckCountHandlesBatchedAndDefaultAcks() {
        assertEquals(1, AaPostTls.parseMediaAckCount(AaPb.u32(1, 0))) // session only -> default 1
        assertEquals(4, AaPostTls.parseMediaAckCount(AaPb.cat(AaPb.u32(1, 0), AaPb.u32(2, 4))))
        assertEquals(1, AaPostTls.parseMediaAckCount(ByteArray(0)))
    }

    @Test
    fun parseSensorEventDecodesCarGpsLocationWithNegativeLongitude() {
        fun int32Field(field: Int, value: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            out.write((field shl 3) or 0)
            var v = value.toLong() // sign-extended 64-bit two's complement per protobuf int32 spec
            while ((v and -128L) != 0L) {
                out.write(((v.toInt() and 0x7F) or 0x80))
                v = v ushr 7
            }
            out.write(v.toInt() and 0x7F)
            return out.toByteArray()
        }
        val locMsg = AaPb.cat(
            int32Field(2, 407_128_000),   // +40.7128 deg
            int32Field(3, -740_060_000),  // -74.0060 deg (negative 10-byte varint!)
            int32Field(4, 4_500),         // 4.5m accuracy
            int32Field(6, 15_000),        // 15.0 m/s
            int32Field(7, 270_000_000)    // 270.0 deg bearing
        )
        val batch = AaPb.emb(AaPostTls.SENSOR_LOCATION, locMsg)
        val state = AaPostTls.parseSensorEvent(batch, AaPostTls.CarSensorState())
        val loc = state.location!!
        assertEquals(40.7128, loc.lat, 1e-4)
        assertEquals(-74.0060, loc.lon, 1e-4)
        assertEquals(4.5f, loc.accuracyM!!, 0.01f)
        assertEquals(15.0f, loc.speedMps!!, 0.01f)
        assertEquals(270.0f, loc.bearingDeg!!, 0.01f)
    }
}
