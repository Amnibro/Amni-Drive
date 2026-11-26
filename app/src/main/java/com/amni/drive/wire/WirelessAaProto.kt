package com.amni.drive.wire

import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Framing and protobuf codecs for Wireless Android Auto Bluetooth RFCOMM bootstrap.
 * Wire format: [uint16 BE payloadLen][uint16 BE msgId][protobuf payload].
 */
object WirelessAaProto {
    val AA_WIRELESS_UUID: UUID = UUID.fromString("4de17a00-52cb-11e6-bdf4-0800200c9a66")
    const val DEFAULT_TCP_PORT = 5277

    const val MSG_WIFI_START_REQUEST = 1
    const val MSG_WIFI_INFO_REQUEST = 2
    const val MSG_WIFI_INFO_RESPONSE = 3
    const val MSG_WIFI_VERSION_REQUEST = 4
    const val MSG_WIFI_VERSION_RESPONSE = 5
    const val MSG_WIFI_CONNECT_STATUS = 6
    const val MSG_WIFI_START_RESPONSE = 7

    const val SEC_OPEN = 1
    const val SEC_WPA_PERSONAL = 4
    const val SEC_WPA2_PERSONAL = 5
    const val SEC_WPA_WPA2_PERSONAL = 6
    const val SEC_WPA3_PERSONAL = 10

    data class Frame(val msgId: Int, val payload: ByteArray)

    data class WifiStartRequest(val ipAddress: String, val port: Int)

    data class WifiInfoResponse(
        val ssid: String,
        val key: String,
        val bssid: String = "",
        val securityMode: Int = SEC_WPA2_PERSONAL,
        val apType: Int = 0
    )

    data class WifiVersion(val major: Int, val minor: Int)

    fun encodeFrame(msgId: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        val len = payload.size
        val out = ByteArray(4 + len)
        out[0] = ((len ushr 8) and 0xFF).toByte()
        out[1] = (len and 0xFF).toByte()
        out[2] = ((msgId ushr 8) and 0xFF).toByte()
        out[3] = (msgId and 0xFF).toByte()
        if (len > 0) System.arraycopy(payload, 0, out, 4, len)
        return out
    }

    fun readFrame(inp: InputStream): Frame? {
        val hdr = readExact(inp, 4) ?: return null
        val len = ((hdr[0].toInt() and 0xFF) shl 8) or (hdr[1].toInt() and 0xFF)
        val msgId = ((hdr[2].toInt() and 0xFF) shl 8) or (hdr[3].toInt() and 0xFF)
        if (len < 0 || len > 65_535) return null
        val body = if (len > 0) (readExact(inp, len) ?: return null) else ByteArray(0)
        return Frame(msgId, body)
    }

    fun writeFrame(out: OutputStream, msgId: Int, payload: ByteArray = ByteArray(0)) {
        val bytes = encodeFrame(msgId, payload)
        synchronized(out) {
            out.write(bytes)
            out.flush()
        }
    }

    fun encodeWifiStartRequest(ip: String, port: Int = DEFAULT_TCP_PORT): ByteArray =
        AaPb.cat(AaPb.str(1, ip), AaPb.u32(2, port))

    fun encodeWifiStartResponse(ip: String, port: Int = DEFAULT_TCP_PORT, status: Int = 0): ByteArray =
        AaPb.cat(AaPb.str(1, ip), AaPb.u32(2, port), AaPb.u32(3, status))

    fun encodeWifiVersionResponse(major: Int = 1, minor: Int = 7, status: Int = 0): ByteArray =
        AaPb.cat(AaPb.u32(1, major), AaPb.u32(2, minor), AaPb.u32(3, status))

    fun encodeWifiConnectStatus(status: Int = 0): ByteArray =
        AaPb.u32(1, status)

    fun parseWifiStartRequest(body: ByteArray): WifiStartRequest {
        var ip = ""
        var port = DEFAULT_TCP_PORT
        val p = intArrayOf(0)
        while (p[0] < body.size) {
            val tag = readVarint(body, p).toInt()
            val f = tag ushr 3
            val w = tag and 7
            when {
                f == 1 && w == 2 -> ip = readString(body, p) ?: break
                f == 2 && w == 0 -> port = readVarint(body, p).toInt().takeIf { it in 1..65535 } ?: DEFAULT_TCP_PORT
                !skipField(body, p, w) -> break
            }
        }
        return WifiStartRequest(ip, port)
    }

    fun parseWifiInfoResponse(body: ByteArray): WifiInfoResponse {
        var ssid = ""
        var key = ""
        var bssid = ""
        var sec = SEC_WPA2_PERSONAL
        var apType = 0
        val p = intArrayOf(0)
        while (p[0] < body.size) {
            val tag = readVarint(body, p).toInt()
            val f = tag ushr 3
            val w = tag and 7
            when {
                f == 1 && w == 2 -> ssid = readString(body, p) ?: break
                f == 2 && w == 2 -> key = readString(body, p) ?: break
                f == 3 && w == 2 -> bssid = readString(body, p) ?: break
                f == 4 && w == 0 -> sec = readVarint(body, p).toInt()
                f == 5 && w == 0 -> apType = readVarint(body, p).toInt()
                !skipField(body, p, w) -> break
            }
        }
        return WifiInfoResponse(ssid, key, bssid, sec, apType)
    }

    fun parseWifiVersion(body: ByteArray): WifiVersion {
        var major = 1
        var minor = 7
        val p = intArrayOf(0)
        while (p[0] < body.size) {
            val tag = readVarint(body, p).toInt()
            val f = tag ushr 3
            val w = tag and 7
            when {
                f == 1 && w == 0 -> major = readVarint(body, p).toInt()
                f == 2 && w == 0 -> minor = readVarint(body, p).toInt()
                !skipField(body, p, w) -> break
            }
        }
        return WifiVersion(major, minor)
    }

    private fun readExact(inp: InputStream, count: Int): ByteArray? {
        val out = ByteArray(count)
        var off = 0
        while (off < count) {
            val n = inp.read(out, off, count - off)
            if (n <= 0) return null
            off += n
        }
        return out
    }

    private fun readVarint(buf: ByteArray, pos: IntArray): Long {
        var i = pos[0]
        var n = 0L
        var shift = 0
        while (i < buf.size) {
            val x = buf[i++].toInt() and 0xFF
            n = n or ((x and 0x7F).toLong() shl shift)
            if (x and 0x80 == 0) break
            shift += 7
        }
        pos[0] = i
        return n
    }

    private fun readString(buf: ByteArray, pos: IntArray): String? {
        val len = readVarint(buf, pos).toInt()
        if (len < 0 || pos[0] + len > buf.size) return null
        val s = String(buf, pos[0], len, Charsets.UTF_8)
        pos[0] += len
        return s
    }

    private fun skipField(buf: ByteArray, pos: IntArray, wire: Int): Boolean {
        when (wire) {
            0 -> readVarint(buf, pos)
            1 -> pos[0] += 8
            2 -> {
                val len = readVarint(buf, pos).toInt()
                if (len < 0 || pos[0] + len > buf.size) return false
                pos[0] += len
            }
            5 -> pos[0] += 4
            else -> return false
        }
        return pos[0] <= buf.size
    }
}
