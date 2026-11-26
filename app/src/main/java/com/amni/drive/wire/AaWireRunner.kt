package com.amni.drive.wire

import android.content.Context
import java.io.ByteArrayOutputStream

/**
 * Shared AAP session runner used by both wired USB AOA ([AoaProbeService]) and
 * Wireless Android Auto TCP ([WirelessAaService]):
 *   Version negotiation (with capped reply) -> Framed TLS -> Post-TLS live projection,
 *   touch/keys/rotary input, sensors, Bluetooth pairing, and optional AAP PCM audio.
 */
object AaWireRunner {
    data class SessionSummary(
        val aapOk: Boolean,
        val tlsOk: Boolean,
        val postOk: Boolean,
        val rxFrames: Int,
        val notes: String
    )

    fun runSession(
        ctx: Context,
        io: AaByteIo,
        label: String,
        onStatus: (String) -> Unit,
        log: (String) -> Unit
    ): SessionSummary {
        var aapOk = false
        var tlsOk = false
        var postOk = false
        var rxFrames = 0
        var notes = ""
        val seed = ByteArrayOutputStream()
        var repliedVersion = false
        var sslSeed = emptyList<ByteArray>()

        // Phase A: fast response when head unit sends VersionRequest immediately
        val t0 = System.currentTimeMillis()
        while (io.isAlive() && System.currentTimeMillis() - t0 < 1_200L) {
            val chunk = io.waitDrain(80)
            if (chunk.isEmpty()) continue
            seed.write(chunk)
            val frames = AaAapVersion.scanFrames(seed.toByteArray(), log, "$label-A")
            val req = frames.firstOrNull { it.msgId == AaAapVersion.MSG_VERSION_REQUEST }
            if (req != null && !repliedVersion) {
                val maj = if (req.payload.size >= 2) ((req.payload[0].toInt() and 0xFF) shl 8) or (req.payload[1].toInt() and 0xFF) else 1
                val min = if (req.payload.size >= 4) ((req.payload[2].toInt() and 0xFF) shl 8) or (req.payload[3].toInt() and 0xFF) else 1
                val (rMaj, rMin) = AaAapVersion.cappedVersionReply(maj, min)
                val rsp = AaAapVersion.versionResponse(rMaj, rMin, AaAapVersion.STATUS_OK)
                io.write(rsp)
                repliedVersion = true
                aapOk = true
                log("$label TX VersionResponse hu=$maj.$min reply=$rMaj.$rMin ${rsp.size}B")
                seed.reset()
            }
            val ssl = AaAapVersion.sslBodiesOf(frames)
            if (ssl.isNotEmpty()) {
                sslSeed = ssl
                if (repliedVersion) break
            }
            if (repliedVersion && System.currentTimeMillis() - t0 > 900L) break
        }

        // Phase B: wait for ClientHello or run active version negotiation fallback
        onStatus("$label · AAP version")
        runCatching {
            if (repliedVersion) {
                if (sslSeed.isEmpty()) {
                    val wait = ByteArrayOutputStream()
                    val tw = System.currentTimeMillis()
                    while (io.isAlive() && System.currentTimeMillis() - tw < 2_500L) {
                        val c = io.waitDrain(80)
                        if (c.isNotEmpty()) wait.write(c)
                        val fr = AaAapVersion.scanFrames(wait.toByteArray(), log, "$label-AAP")
                        sslSeed = AaAapVersion.sslBodiesOf(fr)
                        if (sslSeed.isNotEmpty()) break
                    }
                }
                aapOk = true
            } else {
                val neg = AaAapVersion.negotiateBulk(io, log, 12_000L, seed.toByteArray())
                aapOk = neg.ok
                if (sslSeed.isEmpty()) sslSeed = neg.sslBodies
            }
        }.onFailure { log("$label AAP negotiate error: ${it.message}") }

        // Phase C: Framed TLS handshake
        onStatus("$label · TLS")
        var ssl: AoaSslSession? = null
        runCatching {
            ssl = AoaSslSession.fromBulk(ctx, io, log)
            tlsOk = ssl!!.handshakeFramedBulk(io, 12_000L, sslSeed)
            log("$label TLS ok=$tlsOk aap=$aapOk")
        }.onFailure { log("$label TLS error: ${it.message}") }

        // Phase D: Live encrypted AAP session
        if (tlsOk && ssl != null) {
            onStatus("$label · Amni-Drive Live")
            runCatching {
                val post = AaPostTls.runLiveSession(io, ssl!!, log, 3_600_000L) { spec ->
                    val eng = AmniProjectionEngine(
                        context = ctx.applicationContext,
                        width = spec.width,
                        height = spec.height,
                        dpi = spec.dpi,
                        fps = spec.fps,
                        widthMargin = spec.widthMargin,
                        heightMargin = spec.heightMargin
                    )
                    eng.onLog = { log("$label proj $it") }
                    ProjectionHostService.active = eng
                    eng
                }
                postOk = post.discovery || post.rxFrames > 0
                rxFrames = post.rxFrames
                notes = post.notes
                if (ProjectionHostService.active != null) ProjectionHostService.active = null
                log("$label session end discovery=${post.discovery} videoCh=${post.videoChannel} rx=$rxFrames notes=$notes")
            }.onFailure {
                if (ProjectionHostService.active != null) ProjectionHostService.active = null
                log("$label post-TLS error: ${it.message}")
            }
        }
        return SessionSummary(aapOk, tlsOk, postOk, rxFrames, notes)
    }
}
