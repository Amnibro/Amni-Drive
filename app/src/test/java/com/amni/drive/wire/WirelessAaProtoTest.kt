package com.amni.drive.wire

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class WirelessAaProtoTest {

    @Test
    fun rfcommFrameRoundTripHandlesStartInfoAndVersion() {
        val out = ByteArrayOutputStream()
        val infoPayload = AaPb.cat(
            AaPb.str(1, "Kia_Niro_5G"),
            AaPb.str(2, "supersecret99"),
            AaPb.str(3, "11:22:33:44:55:66"),
            AaPb.u32(4, WirelessAaProto.SEC_WPA2_PERSONAL),
            AaPb.u32(5, 1)
        )
        WirelessAaProto.writeFrame(out, WirelessAaProto.MSG_WIFI_START_REQUEST, WirelessAaProto.encodeWifiStartRequest("192.168.43.1", 5277))
        WirelessAaProto.writeFrame(out, WirelessAaProto.MSG_WIFI_INFO_RESPONSE, infoPayload)
        WirelessAaProto.writeFrame(out, WirelessAaProto.MSG_WIFI_VERSION_REQUEST, AaPb.cat(AaPb.u32(1, 1), AaPb.u32(2, 7)))

        val inp = ByteArrayInputStream(out.toByteArray())
        val f1 = WirelessAaProto.readFrame(inp)
        assertNotNull(f1)
        assertEquals(WirelessAaProto.MSG_WIFI_START_REQUEST, f1!!.msgId)
        val start = WirelessAaProto.parseWifiStartRequest(f1.payload)
        assertEquals("192.168.43.1", start.ipAddress)
        assertEquals(5277, start.port)

        val f2 = WirelessAaProto.readFrame(inp)
        assertNotNull(f2)
        assertEquals(WirelessAaProto.MSG_WIFI_INFO_RESPONSE, f2!!.msgId)
        val info = WirelessAaProto.parseWifiInfoResponse(f2.payload)
        assertEquals("Kia_Niro_5G", info.ssid)
        assertEquals("supersecret99", info.key)
        assertEquals("11:22:33:44:55:66", info.bssid)
        assertEquals(WirelessAaProto.SEC_WPA2_PERSONAL, info.securityMode)

        val f3 = WirelessAaProto.readFrame(inp)
        assertNotNull(f3)
        assertEquals(WirelessAaProto.MSG_WIFI_VERSION_REQUEST, f3!!.msgId)
        val ver = WirelessAaProto.parseWifiVersion(f3.payload)
        assertEquals(1, ver.major)
        assertEquals(7, ver.minor)
    }
}
