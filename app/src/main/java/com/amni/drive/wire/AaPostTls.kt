package com.amni.drive.wire

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

object AaPostTls {
    const val MSG_AUTH_COMPLETE = 0x0004
    const val MSG_SERVICE_DISCOVERY_REQUEST = 0x0005
    const val MSG_SERVICE_DISCOVERY_RESPONSE = 0x0006
    const val MSG_CHANNEL_OPEN_REQUEST = 0x0007
    const val MSG_CHANNEL_OPEN_RESPONSE = 0x0008
    const val MSG_CHANNEL_CLOSE_NOTIFICATION = 0x0009
    const val MSG_PING_REQUEST = 0x000b
    const val MSG_PING_RESPONSE = 0x000c
    const val MSG_NAV_FOCUS_REQUEST = 0x000d
    const val MSG_NAV_FOCUS_RESPONSE = 0x000e
    const val MSG_SHUTDOWN_REQUEST = 0x000f
    const val MSG_SHUTDOWN_RESPONSE = 0x0010
    const val MSG_AUDIO_FOCUS_REQUEST = 0x0012
    const val MSG_AUDIO_FOCUS_RESPONSE = 0x0013
    const val MSG_UNKNOWN_FF = 0x00ff

    // Media sink messages (AARE MediaMsg)
    const val MEDIA_DATA = 0x0000
    const val MEDIA_CODEC_CONFIG = 0x0001
    const val MEDIA_SETUP = 0x8000
    const val MEDIA_START = 0x8001
    const val MEDIA_STOP = 0x8002
    const val MEDIA_CONFIG = 0x8003
    const val MEDIA_ACK = 0x8004
    const val MEDIA_VIDEO_FOCUS_REQUEST = 0x8007
    const val MEDIA_VIDEO_FOCUS_NOTIFICATION = 0x8008
    const val MEDIA_UI_CONFIG_REQUEST = 0x8009
    const val MEDIA_UI_CONFIG_REPLY = 0x800A

    // Input channel messages (AARE InputMsg)
    const val INPUT_REPORT = 0x8001
    const val INPUT_KEY_BINDING_REQUEST = 0x8002
    const val INPUT_KEY_BINDING_RESPONSE = 0x8003

    const val CH_CONTROL = 0
    const val CH_VIDEO = 1
    const val CH_INPUT = 2
    const val CH_SENSOR = 3
    const val STATUS_OK = 0

    data class VideoSpec(
        val width: Int = 1920,
        val height: Int = 720,
        val fps: Int = 30,
        val dpi: Int = 160,
        val widthMargin: Int = 0,
        val heightMargin: Int = 0
    )

    data class TouchSpec(val width: Int, val height: Int)

    data class KeyEvt(val keycode: Int, val down: Boolean)

    data class RelEvt(val keycode: Int, val delta: Int)

    data class ChanInfo(
        val id: Int,
        val kind: String,
        val videoSpecs: List<VideoSpec> = emptyList(),
        val touchSpec: TouchSpec? = null,
        val keys: List<Int> = emptyList()
    ) {
        val videoSpec: VideoSpec? get() = videoSpecs.firstOrNull()
    }

    data class Result(val discovery: Boolean, val videoChannel: Int, val rxFrames: Int, val notes: String)

    fun authCompleteBody(): ByteArray = AaPb.u32(1, STATUS_OK)

    fun serviceDiscoveryRequestBody(
        name: String = "AmniDrive",
        brand: String = "Amni"
    ): ByteArray = AaPb.cat(AaPb.str(4, name), AaPb.str(5, brand))

    fun channelOpenRequestBody(channelId: Int, priority: Int = 0): ByteArray =
        AaPb.cat(AaPb.u32(1, priority), AaPb.u32(2, channelId))

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

    fun parseStatus(body: ByteArray): Int {
        val pos = intArrayOf(0)
        while (pos[0] < body.size) {
            val tag = readVarint(body, pos).toInt()
            val field = tag ushr 3
            val wire = tag and 7
            if (wire == 0) {
                val v = readVarint(body, pos).toInt()
                if (field == 1) return v
            } else if (!skipField(body, pos, wire)) break
        }
        return -1
    }

    fun parseChannelId(body: ByteArray): Int {
        val pos = intArrayOf(0)
        while (pos[0] < body.size) {
            val tag = readVarint(body, pos).toInt()
            val field = tag ushr 3
            val wire = tag and 7
            if (wire == 0) {
                val v = readVarint(body, pos).toInt()
                if (field == 2) return v
            } else if (!skipField(body, pos, wire)) break
        }
        return -1
    }

    private fun parseVideoConfig(vc: ByteArray): VideoSpec {
        var resType = 1
        var fpsType = 2
        var wMargin = 0
        var hMargin = 0
        var density = 160
        val pos = intArrayOf(0)
        while (pos[0] < vc.size) {
            val tag = readVarint(vc, pos).toInt()
            val field = tag ushr 3
            val wire = tag and 7
            if (wire == 0) {
                val v = readVarint(vc, pos).toInt()
                when (field) {
                    1 -> resType = v
                    2 -> fpsType = v
                    3 -> wMargin = v
                    4 -> hMargin = v
                    5 -> density = v
                }
            } else if (!skipField(vc, pos, wire)) break
        }
        val (w, h) = when (resType) {
            2 -> 1280 to 720
            3 -> 1920 to 1080
            4 -> 2560 to 1440
            5 -> 3840 to 2160
            6 -> 720 to 1280
            7 -> 1080 to 1920
            else -> 800 to 480
        }
        val fps = if (fpsType == 1) 30 else 30
        return VideoSpec(
            width = w,
            height = h,
            fps = fps,
            dpi = density.coerceIn(120, 480),
            widthMargin = wMargin.coerceIn(0, w - 100),
            heightMargin = hMargin.coerceIn(0, h - 100)
        )
    }

    private fun parseInputKeys(sub: ByteArray): List<Int> {
        val out = ArrayList<Int>()
        val pos = intArrayOf(0)
        while (pos[0] < sub.size) {
            val tag = readVarint(sub, pos).toInt()
            val field = tag ushr 3
            val wire = tag and 7
            when {
                field == 1 && wire == 0 -> out += readVarint(sub, pos).toInt()
                field == 1 && wire == 2 -> { val len = readVarint(sub, pos).toInt(); val end = pos[0] + len; if (len < 0 || end > sub.size) break; while (pos[0] < end) out += readVarint(sub, pos).toInt() }
                !skipField(sub, pos, wire) -> break
            }
        }
        return out
    }
    private fun parseEvents(body: ByteArray, field: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        sub(body, field).forEach { group -> sub(group, 1).forEach { ev -> var code = 0; var v = 0; val p = intArrayOf(0); while (p[0] < ev.size) { val tag = readVarint(ev, p).toInt(); if (tag and 7 == 0) { val n = readVarint(ev, p).toInt(); if (tag ushr 3 == 1) code = n else if (tag ushr 3 == 2) v = n } else if (!skipField(ev, p, tag and 7)) break }; out += code to v } }
        return out
    }
    private fun sub(buf: ByteArray, field: Int): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        val p = intArrayOf(0)
        while (p[0] < buf.size) { val tag = readVarint(buf, p).toInt(); if (tag and 7 == 2) { val len = readVarint(buf, p).toInt(); if (len < 0 || p[0] + len > buf.size) break; if (tag ushr 3 == field) out += buf.copyOfRange(p[0], p[0] + len); p[0] += len } else if (!skipField(buf, p, tag and 7)) break }
        return out
    }
    private fun parseInputTouchScreen(sub: ByteArray): TouchSpec? {
        val pos = intArrayOf(0)
        while (pos[0] < sub.size) {
            val tag = readVarint(sub, pos).toInt()
            val field = tag ushr 3
            val wire = tag and 7
            if (wire == 2) {
                val len = readVarint(sub, pos).toInt()
                if (len < 0 || pos[0] + len > sub.size) break
                val ts = sub.copyOfRange(pos[0], pos[0] + len)
                pos[0] += len
                if (field == 2) {
                    var w = 0
                    var h = 0
                    var secondary = false
                    val tp = intArrayOf(0)
                    while (tp[0] < ts.size) {
                        val t = readVarint(ts, tp).toInt()
                        val tf = t ushr 3
                        val tw = t and 7
                        if (tw == 0) {
                            val v = readVarint(ts, tp).toInt()
                            when (tf) {
                                1 -> w = v
                                2 -> h = v
                                4 -> secondary = (v != 0)
                            }
                        } else if (!skipField(ts, tp, tw)) break
                    }
                    if (!secondary && w > 0 && h > 0) return TouchSpec(w, h)
                }
            } else if (!skipField(sub, pos, wire)) break
        }
        return null
    }

    fun parseChannels(body: ByteArray): List<ChanInfo> {
        val out = ArrayList<ChanInfo>()
        val pos = intArrayOf(0)
        while (pos[0] < body.size) {
            val tag = readVarint(body, pos).toInt()
            val field = tag ushr 3
            val wire = tag and 7
            if (wire == 2) {
                val len = readVarint(body, pos).toInt()
                if (len < 0 || pos[0] + len > body.size) break
                val emb = body.copyOfRange(pos[0], pos[0] + len)
                pos[0] += len
                if (field == 1) parseService(emb)?.let { out.add(it) }
            } else if (!skipField(body, pos, wire)) break
        }
        return out
    }

    private fun parseService(emb: ByteArray): ChanInfo? {
        var id = -1
        var kind = "unk"
        var videoSpecs = emptyList<VideoSpec>()
        var touchSpec: TouchSpec? = null
        var keys = emptyList<Int>()
        val pos = intArrayOf(0)
        while (pos[0] < emb.size) {
            val tag = readVarint(emb, pos).toInt()
            val field = tag ushr 3
            val wire = tag and 7
            when {
                wire == 0 -> {
                    val v = readVarint(emb, pos).toInt()
                    if (field == 1) id = v
                }
                wire == 2 -> {
                    val len = readVarint(emb, pos).toInt()
                    if (len < 0 || pos[0] + len > emb.size) break
                    val sub = emb.copyOfRange(pos[0], pos[0] + len)
                    pos[0] += len
                    when (field) {
                        2 -> kind = "sensor"
                        3 -> {
                            kind = "av"
                            videoSpecs = parseMediaSinkVideo(sub)
                        }
                        4 -> {
                            kind = "input"
                            touchSpec = parseInputTouchScreen(sub)
                            keys = parseInputKeys(sub)
                        }
                        5 -> kind = "avin"
                        6 -> kind = "bt"
                        8 -> kind = "nav"
                        12 -> kind = "vendor"
                    }
                }
                !skipField(emb, pos, wire) -> break
            }
        }
        return if (id >= 0) ChanInfo(id, kind, videoSpecs, touchSpec, keys) else null
    }

    private fun parseMediaSinkVideo(sub: ByteArray): List<VideoSpec> {
        val specs = ArrayList<VideoSpec>()
        val pos = intArrayOf(0)
        while (pos[0] < sub.size) {
            val tag = readVarint(sub, pos).toInt()
            val field = tag ushr 3
            val wire = tag and 7
            when {
                wire == 2 -> {
                    val len = readVarint(sub, pos).toInt()
                    if (len < 0 || pos[0] + len > sub.size) break
                    val vc = sub.copyOfRange(pos[0], pos[0] + len)
                    pos[0] += len
                    if (field == 4) {
                        specs.add(parseVideoConfig(vc))
                    }
                }
                !skipField(sub, pos, wire) -> break
            }
        }
        return specs
    }

    fun selectVideoConfig(specs: List<VideoSpec>, indices: List<Int>): Pair<Int, VideoSpec> {
        if (specs.isEmpty()) return (indices.firstOrNull() ?: 0) to VideoSpec()
        val candidates = if (indices.isNotEmpty()) indices else specs.indices.toList()
        val valid = candidates.mapNotNull { idx -> specs.getOrNull(idx)?.let { idx to it } }
        val chosen = valid
            .filter { (_, s) -> s.width <= 1920 && s.height <= 1080 }
            .maxByOrNull { (_, s) -> s.width.toLong() * s.height.toLong() }
            ?: valid.firstOrNull()
            ?: (0 to specs.first())
        return chosen
    }

    fun keyBindingRequestBody(keycodes: List<Int>): ByteArray {
        val list = if (keycodes.isNotEmpty()) keycodes else listOf(3, 4, 19, 20, 21, 22, 23, 66, 85, 87, 88, 260, 261)
        val out = ByteArrayOutputStream()
        for (code in list.distinct()) {
            out.write(AaPb.u32(1, code))
        }
        return out.toByteArray()
    }

    fun parseKeyEvents(body: ByteArray): List<KeyEvt> {
        val out = ArrayList<KeyEvt>()
        val pos = intArrayOf(0)
        while (pos[0] < body.size) {
            val tag = readVarint(body, pos).toInt()
            val field = tag ushr 3
            val wire = tag and 7
            if (wire == 2) {
                val len = readVarint(body, pos).toInt()
                if (len < 0 || pos[0] + len > body.size) break
                val kb = body.copyOfRange(pos[0], pos[0] + len)
                pos[0] += len
                if (field == 4) {
                    val kp = intArrayOf(0)
                    while (kp[0] < kb.size) {
                        val kt = readVarint(kb, kp).toInt()
                        val kf = kt ushr 3
                        val kw = kt and 7
                        if (kw == 2) {
                            val klen = readVarint(kb, kp).toInt()
                            if (klen < 0 || kp[0] + klen > kb.size) break
                            val entry = kb.copyOfRange(kp[0], kp[0] + klen)
                            kp[0] += klen
                            if (kf == 1) {
                                var code = -1
                                var down = false
                                val ep = intArrayOf(0)
                                while (ep[0] < entry.size) {
                                    val et = readVarint(entry, ep).toInt()
                                    val ef = et ushr 3
                                    val ew = et and 7
                                    if (ew == 0) {
                                        val ev = readVarint(entry, ep).toInt()
                                        when (ef) {
                                            1 -> code = ev
                                            2 -> down = ev != 0
                                        }
                                    } else if (!skipField(entry, ep, ew)) break
                                }
                                if (code >= 0) out.add(KeyEvt(code, down))
                            }
                        } else if (!skipField(kb, kp, kw)) break
                    }
                }
            } else if (!skipField(body, pos, wire)) break
        }
        return out
    }

    fun parseRelativeEvents(body: ByteArray): List<RelEvt> {
        val out = ArrayList<RelEvt>()
        val pos = intArrayOf(0)
        while (pos[0] < body.size) {
            val tag = readVarint(body, pos).toInt()
            val field = tag ushr 3
            val wire = tag and 7
            if (wire == 2) {
                val len = readVarint(body, pos).toInt()
                if (len < 0 || pos[0] + len > body.size) break
                val rb = body.copyOfRange(pos[0], pos[0] + len)
                pos[0] += len
                if (field == 6) {
                    val rp = intArrayOf(0)
                    while (rp[0] < rb.size) {
                        val rt = readVarint(rb, rp).toInt()
                        val rf = rt ushr 3
                        val rw = rt and 7
                        if (rw == 2) {
                            val rlen = readVarint(rb, rp).toInt()
                            if (rlen < 0 || rp[0] + rlen > rb.size) break
                            val entry = rb.copyOfRange(rp[0], rp[0] + rlen)
                            rp[0] += rlen
                            if (rf == 1) {
                                var code = 0
                                var delta = 0
                                val ep = intArrayOf(0)
                                while (ep[0] < entry.size) {
                                    val et = readVarint(entry, ep).toInt()
                                    val ef = et ushr 3
                                    val ew = et and 7
                                    if (ew == 0) {
                                        val ev = readVarint(entry, ep).toInt()
                                        when (ef) {
                                            1 -> code = ev
                                            2 -> delta = ev
                                        }
                                    } else if (!skipField(entry, ep, ew)) break
                                }
                                if (delta != 0) out.add(RelEvt(code, delta))
                            }
                        } else if (!skipField(rb, rp, rw)) break
                    }
                }
            } else if (!skipField(body, pos, wire)) break
        }
        return out
    }

    private data class TouchPtr(val x: Float, val y: Float, val id: Int)

    private fun parseTouchReport(body: ByteArray): Triple<Int, List<TouchPtr>, Int>? {
        var touchBytes: ByteArray? = null
        val pos = intArrayOf(0)
        while (pos[0] < body.size) {
            val tag = readVarint(body, pos).toInt()
            val field = tag ushr 3
            val wire = tag and 7
            if (wire == 2) {
                val len = readVarint(body, pos).toInt()
                if (len < 0 || pos[0] + len > body.size) break
                if (field == 3) touchBytes = body.copyOfRange(pos[0], pos[0] + len)
                pos[0] += len
            } else if (!skipField(body, pos, wire)) break
        }
        val tb = touchBytes ?: return null
        val ptrs = ArrayList<TouchPtr>()
        var actionIdx = 0
        var action = -1
        val tp = intArrayOf(0)
        while (tp[0] < tb.size) {
            val tag = readVarint(tb, tp).toInt()
            val field = tag ushr 3
            val wire = tag and 7
            when {
                wire == 0 -> {
                    val v = readVarint(tb, tp).toInt()
                    when (field) {
                        2 -> actionIdx = v
                        3 -> action = v
                    }
                }
                wire == 2 -> {
                    val len = readVarint(tb, tp).toInt()
                    if (len < 0 || tp[0] + len > tb.size) break
                    val pb = tb.copyOfRange(tp[0], tp[0] + len)
                    tp[0] += len
                    if (field == 1) {
                        var px = 0f
                        var py = 0f
                        var pid = 0
                        val pp = intArrayOf(0)
                        while (pp[0] < pb.size) {
                            val pt = readVarint(pb, pp).toInt()
                            val pf = pt ushr 3
                            val pw = pt and 7
                            if (pw == 0) {
                                val pv = readVarint(pb, pp).toInt()
                                when (pf) {
                                    1 -> px = pv.toFloat()
                                    2 -> py = pv.toFloat()
                                    3 -> pid = pv
                                }
                            } else if (!skipField(pb, pp, pw)) break
                        }
                        ptrs.add(TouchPtr(px, py, pid))
                    }
                }
                !skipField(tb, tp, wire) -> break
            }
        }
        return if (action >= 0 && ptrs.isNotEmpty()) Triple(action, ptrs, actionIdx) else null
    }

    /**
     * Runs the complete encrypted post-TLS session until disconnect, EOF, or ByeBye:
     * Auth -> ServiceDiscovery -> ChannelOpen (with TYPE_CONTROL!) -> AVSetup -> Config ->
     * Start + VideoFocus -> live H.264 video + touch injection + Ping/Pong keepalive.
     */
    fun runLiveSession(
        io: AoaBulkIo,
        ssl: AoaSslSession,
        log: (String) -> Unit,
        maxDurationMs: Long = 3_600_000L,
        createProjection: (VideoSpec) -> AmniProjectionEngine?
    ): Result {
        var discovery = false
        var videoCh = -1
        var inputCh = -1
        var sensorCh = -1
        var rxFrames = 0
        var sdSent = 0
        var opened = 0
        val notes = StringBuilder()
        var videoSpec = VideoSpec()
        var videoSpecs: List<VideoSpec> = emptyList()
        var inputKeys: List<Int> = emptyList()
        var touchSpec: TouchSpec? = null
        var engine: AmniProjectionEngine? = null
        var videoStarted = false
        var videoFocused = false
        var pendingStartIdx = -1
        var waitConfigDeadlineMs = 0L
        val codecConfigBytes = java.util.concurrent.atomic.AtomicReference<ByteArray?>(null)
        val unacked = AtomicInteger(0)
        val maxUnacked = AtomicInteger(2)
        val ackSeen = java.util.concurrent.atomic.AtomicBoolean(false)
        var touchDownTime = 0L

        val txLock = Any()

        val maxPayload = 16384

        fun txAapPayload(chId: Int, plain: ByteArray, control: Boolean, tag: String, quiet: Boolean = false) {
            val baseFlags = AaAapVersion.ENC_ENCRYPTED or (if (control) AaAapVersion.TYPE_CONTROL else 0)
            try {
                var offset = 0
                val totalLen = plain.size
                synchronized(txLock) {
                    do {
                        val chunkLen = minOf(maxPayload, totalLen - offset)
                        val first = offset == 0
                        val last = offset + chunkLen == totalLen
                        var flags = baseFlags
                        if (first) flags = flags or AaAapVersion.FRAME_FIRST
                        if (last) flags = flags or AaAapVersion.FRAME_LAST
                        val extended = first && !last

                        val chunk = if (offset == 0 && chunkLen == totalLen) plain else plain.copyOfRange(offset, offset + chunkLen)
                        val cipher = ssl.encryptApp(chunk)
                        val hdrLen = if (extended) 8 else 4
                        val frame = ByteArray(hdrLen + cipher.size)
                        frame[0] = (chId and 0xFF).toByte()
                        frame[1] = (flags and 0xFF).toByte()
                        frame[2] = ((cipher.size ushr 8) and 0xFF).toByte()
                        frame[3] = (cipher.size and 0xFF).toByte()
                        if (extended) {
                            frame[4] = ((totalLen ushr 24) and 0xFF).toByte()
                            frame[5] = ((totalLen ushr 16) and 0xFF).toByte()
                            frame[6] = ((totalLen ushr 8) and 0xFF).toByte()
                            frame[7] = (totalLen and 0xFF).toByte()
                        }
                        System.arraycopy(cipher, 0, frame, hdrLen, cipher.size)
                        io.write(frame)
                        offset += chunkLen
                    } while (offset < totalLen)
                }
                if (!quiet) log("POST-TLS TX ch=$chId flags=0x${(baseFlags or AaAapVersion.FRAME_BULK).toString(16)} $tag plain=$totalLen")
            } catch (e: Exception) {
                log("POST-TLS TX fail ch=$chId $tag ${e.message}")
            }
        }

        fun txEncrypted(chId: Int, msgId: Int, body: ByteArray, control: Boolean, tag: String, quiet: Boolean = false) {
            val plain = ByteArray(2 + body.size)
            plain[0] = ((msgId ushr 8) and 0xFF).toByte()
            plain[1] = (msgId and 0xFF).toByte()
            if (body.isNotEmpty()) System.arraycopy(body, 0, plain, 2, body.size)
            txAapPayload(chId, plain, control, tag, quiet)
        }

        fun sendMediaData(chId: Int, nalu: ByteArray, isKeyFrame: Boolean, isCodecConfig: Boolean, ptsUs: Long) {
            if (!io.isAlive()) return
            if (isCodecConfig) {
                codecConfigBytes.set(nalu.copyOf())
                txEncrypted(chId, MEDIA_CODEC_CONFIG, nalu, control = false, tag = "CodecConfig", quiet = false)
                return
            }
            if (!videoStarted) return
            if (ackSeen.get() && !isKeyFrame && unacked.get() >= maxUnacked.get() * 2) return
            val plain = ByteArray(2 + 8 + nalu.size)
            plain[0] = (MEDIA_DATA ushr 8).toByte()
            plain[1] = (MEDIA_DATA and 0xFF).toByte()
            ByteBuffer.wrap(plain, 2, 8).order(ByteOrder.BIG_ENDIAN).putLong(ptsUs)
            System.arraycopy(nalu, 0, plain, 10, nalu.size)
            txAapPayload(chId, plain, control = false, tag = "VideoData", quiet = true)
            unacked.incrementAndGet()
        }

        fun startProjectionIfNeeded() {
            if (engine != null || videoCh <= 0) return
            val eng = createProjection(videoSpec) ?: return
            eng.setFrameSinkTyped { bytes, key, cfg, pts ->
                sendMediaData(videoCh, bytes, key, cfg, pts)
            }
            if (eng.start()) {
                engine = eng
                notes.append("proj-ok;")
                log("POST-TLS projection started ${videoSpec.width}x${videoSpec.height} ch=$videoCh")
                codecConfigBytes.get()?.let { cfg ->
                    txEncrypted(videoCh, MEDIA_CODEC_CONFIG, cfg, control = false, tag = "CodecConfig-resend")
                }
                eng.requestKeyFrame()
            } else {
                log("POST-TLS projection engine failed to start")
            }
        }

        fun beginVideoStream(cfgIndex: Int) {
            if (videoStarted || videoCh <= 0) return
            videoStarted = true
            pendingStartIdx = -1
            waitConfigDeadlineMs = 0L
            txEncrypted(videoCh, MEDIA_START, AaPb.cat(AaPb.u32(1, 0), AaPb.u32(2, cfgIndex)), control = false, tag = "VideoStart idx=$cfgIndex")
            txEncrypted(videoCh, MEDIA_VIDEO_FOCUS_REQUEST, AaPb.cat(AaPb.u32(2, 1), AaPb.u32(3, 0)), control = false, tag = "VideoFocusRequest")
            startProjectionIfNeeded()
        }

        val nameVariants = listOf(
            "AmniDrive" to "Amni",
            "Android Auto" to "Google"
        )

        fun sendDiscovery(why: String) {
            val idx = (sdSent++).coerceAtMost(nameVariants.lastIndex)
            val (name, brand) = nameVariants[idx]
            discovery = true
            notes.append("sd-req-$why;")
            txEncrypted(CH_CONTROL, MSG_SERVICE_DISCOVERY_REQUEST, serviceDiscoveryRequestBody(name, brand), control = false, tag = "ServiceDiscoveryRequest-$name")
        }

        fun openChannel(chId: Int, kind: String) {
            if (chId <= 0) return
            notes.append("open-$kind-$chId;")
            // IMPORTANT: ChannelOpenRequest MUST have control = true (TYPE_CONTROL -> flags 0x0f)!
            txEncrypted(chId, MSG_CHANNEL_OPEN_REQUEST, channelOpenRequestBody(chId, 0), control = true, tag = "ChannelOpenRequest-$kind-$chId")
            opened++
        }

        fun handleMsg(channel: Int, msgId: Int, body: ByteArray, via: String) {
            rxFrames++
            if (msgId != MSG_PING_REQUEST && msgId != MEDIA_ACK) {
                log("POST-TLS RX $via ch=$channel msg=0x${msgId.toString(16)} body=${body.size} hex=${AaAapVersion.hexHead(body, 20)}")
            }
            if (channel == CH_CONTROL) {
                when (msgId) {
                    MSG_AUTH_COMPLETE -> {
                        val st = parseStatus(body)
                        AaCertStore.noteAuthRefusal(st)
                        notes.append("auth-st$st;")
                        log("POST-TLS AuthComplete status=$st")
                        if (sdSent == 0) sendDiscovery("after-auth")
                    }
                    MSG_SERVICE_DISCOVERY_RESPONSE -> {
                        notes.append("sd-rsp;")
                        discovery = true
                        val chans = parseChannels(body)
                        log("POST-TLS ServiceDiscoveryResponse channels=${chans.size} ${chans.joinToString { "${it.id}:${it.kind}" }}")
                        val av = chans.firstOrNull { it.kind == "av" && it.videoSpecs.isNotEmpty() } ?: chans.firstOrNull { it.kind == "av" }
                        val inp = chans.firstOrNull { it.kind == "input" }
                        val sens = chans.firstOrNull { it.kind == "sensor" }
                        if (av != null) {
                            videoCh = av.id
                            videoSpecs = av.videoSpecs
                            av.videoSpec?.let { videoSpec = it }
                            log("POST-TLS picked videoCh=$videoCh configs=${videoSpecs.size} primary=${videoSpec.width}x${videoSpec.height} margins=${videoSpec.widthMargin}x${videoSpec.heightMargin} dpi=${videoSpec.dpi}")
                            openChannel(videoCh, "av")
                        } else {
                            videoCh = CH_VIDEO
                            openChannel(videoCh, "av")
                        }
                        if (inp != null) {
                            inputCh = inp.id
                            touchSpec = inp.touchSpec
                            inputKeys = inp.keys
                            log("POST-TLS inputCh=$inputCh touch=${touchSpec?.width}x${touchSpec?.height} keys=${inp.keys}")
                            openChannel(inputCh, "input")
                        }
                        if (sens != null) {
                            sensorCh = sens.id
                            openChannel(sensorCh, "sensor")
                        }
                    }
                    MSG_PING_REQUEST -> txEncrypted(CH_CONTROL, MSG_PING_RESPONSE, body, control = false, tag = "PingResponse", quiet = true)
                    MSG_NAV_FOCUS_REQUEST -> txEncrypted(CH_CONTROL, MSG_NAV_FOCUS_RESPONSE, AaPb.u32(1, 2), control = false, tag = "NavFocusNotification")
                    MSG_AUDIO_FOCUS_REQUEST -> txEncrypted(CH_CONTROL, MSG_AUDIO_FOCUS_RESPONSE, AaPb.u32(1, 1), control = false, tag = "AudioFocusNotification")
                    MSG_SHUTDOWN_REQUEST -> {
                        notes.append("byebye;")
                        txEncrypted(CH_CONTROL, MSG_SHUTDOWN_RESPONSE, ByteArray(0), control = false, tag = "ByeByeResponse")
                    }
                    MSG_UNKNOWN_FF, 0xff -> {
                        notes.append("ff;")
                        log("POST-TLS HU 0xff UnexpectedMessage")
                    }
                    else -> notes.append("ctl-0x${msgId.toString(16)};")
                }
                return
            }

            // Channel-level control messages (ChannelOpenResponse, ChannelClose)
            when (msgId) {
                MSG_CHANNEL_OPEN_RESPONSE -> {
                    val st = parseStatus(body)
                    notes.append("ch$channel-open-st$st;")
                    log("POST-TLS ChannelOpenResponse ch=$channel status=$st")
                    if (st == 0) {
                        when (channel) {
                            videoCh -> {
                                // Setup { type = 3 (MEDIA_CODEC_VIDEO_H264_BP) }
                                txEncrypted(videoCh, MEDIA_SETUP, AaPb.u32(1, 3), control = false, tag = "VideoSetup")
                            }
                            inputCh -> txEncrypted(inputCh, INPUT_KEY_BINDING_REQUEST, keyBindingRequestBody(inputKeys), control = false, tag = "KeyBindingRequest keys=${inputKeys.size}")
                        }
                    }
                    return
                }
                MSG_CHANNEL_CLOSE_NOTIFICATION -> {
                    notes.append("ch$channel-closed;")
                    log("POST-TLS HU closed ch=$channel")
                    return
                }
            }

            // Channel-specific messages
            if (channel == videoCh) {
                when (msgId) {
                    MEDIA_CONFIG -> {
                        // Parse status (field 1), max_unacked (field 2), configuration_indices (field 3, repeated or packed)
                        var cfgStatus = 2
                        var cfgMaxUnacked = 2
                        val indices = ArrayList<Int>()
                        val pos = intArrayOf(0)
                        while (pos[0] < body.size) {
                            val tag = readVarint(body, pos).toInt()
                            val field = tag ushr 3
                            val wire = tag and 7
                            when {
                                wire == 0 -> {
                                    val v = readVarint(body, pos).toInt()
                                    when (field) {
                                        1 -> cfgStatus = v
                                        2 -> cfgMaxUnacked = v
                                        3 -> indices.add(v)
                                    }
                                }
                                wire == 2 && field == 3 -> {
                                    val len = readVarint(body, pos).toInt()
                                    val end = (pos[0] + len).coerceAtMost(body.size)
                                    while (pos[0] < end) indices.add(readVarint(body, pos).toInt())
                                }
                                !skipField(body, pos, wire) -> break
                            }
                        }
                        val (chosenIdx, chosenSpec) = selectVideoConfig(videoSpecs, indices)
                        videoSpec = chosenSpec
                        maxUnacked.set(cfgMaxUnacked.coerceAtLeast(1))
                        notes.append("vcfg-st$cfgStatus;")
                        log("POST-TLS Video Config status=$cfgStatus maxUnacked=${maxUnacked.get()} indices=$indices chosen=$chosenIdx (${chosenSpec.width}x${chosenSpec.height} dpi=${chosenSpec.dpi})")
                        if (!videoStarted) {
                            if (cfgStatus == 1) {
                                // STATUS_WAIT: give HU up to 1200ms to send STATUS_READY, then start anyway
                                pendingStartIdx = chosenIdx
                                waitConfigDeadlineMs = System.currentTimeMillis() + 1_200L
                            } else {
                                beginVideoStream(chosenIdx)
                            }
                        }
                    }
                    MEDIA_ACK -> {
                        ackSeen.set(true)
                        unacked.updateAndGet { maxOf(0, it - 1) }
                    }
                    MEDIA_VIDEO_FOCUS_REQUEST -> {
                        // HU asks focus mode (field 2)
                        var mode = 1
                        val pos = intArrayOf(0)
                        while (pos[0] < body.size) {
                            val tag = readVarint(body, pos).toInt()
                            val field = tag ushr 3
                            val wire = tag and 7
                            if (wire == 0) {
                                val v = readVarint(body, pos).toInt()
                                if (field == 2) mode = v
                            } else if (!skipField(body, pos, wire)) break
                        }
                        txEncrypted(videoCh, MEDIA_VIDEO_FOCUS_NOTIFICATION, AaPb.cat(AaPb.u32(1, mode), AaPb.bool(2, false)), control = false, tag = "VideoFocusReply")
                        videoFocused = (mode == 1 || mode == 3)
                        if (videoFocused) {
                            startProjectionIfNeeded()
                            codecConfigBytes.get()?.let { cfg -> txEncrypted(videoCh, MEDIA_CODEC_CONFIG, cfg, control = false, tag = "CodecConfig-focus") }
                            engine?.requestKeyFrame()
                        }
                    }
                    MEDIA_VIDEO_FOCUS_NOTIFICATION -> {
                        var focus = 1
                        val pos = intArrayOf(0)
                        while (pos[0] < body.size) {
                            val tag = readVarint(body, pos).toInt()
                            val field = tag ushr 3
                            val wire = tag and 7
                            if (wire == 0) {
                                val v = readVarint(body, pos).toInt()
                                if (field == 1) focus = v
                            } else if (!skipField(body, pos, wire)) break
                        }
                        videoFocused = (focus == 1 || focus == 3)
                        log("POST-TLS VideoFocusNotification focus=$focus projected=$videoFocused")
                        if (videoFocused) {
                            startProjectionIfNeeded()
                            codecConfigBytes.get()?.let { cfg -> txEncrypted(videoCh, MEDIA_CODEC_CONFIG, cfg, control = false, tag = "CodecConfig-focusN") }
                            engine?.requestKeyFrame()
                        }
                    }
                    MEDIA_UI_CONFIG_REQUEST -> {
                        txEncrypted(videoCh, MEDIA_UI_CONFIG_REPLY, body, control = false, tag = "UiConfigReply")
                    }
                }
            } else if (channel == inputCh) {
                if (msgId == INPUT_REPORT) {
                    parseKeyEvents(body).forEach { k -> engine?.dispatchKey(k.keycode, k.down) }
                    parseRelativeEvents(body).forEach { r ->
                        if (r.delta != 0) {
                            log("POST-TLS rotary code=${r.keycode} delta=${r.delta}")
                            engine?.moveFocus(
                                if (r.delta > 0) android.view.KeyEvent.KEYCODE_NAVIGATE_NEXT else android.view.KeyEvent.KEYCODE_NAVIGATE_PREVIOUS,
                                kotlin.math.abs(r.delta)
                            )
                        }
                    }
                    parseTouchReport(body)?.let { (act, ptrs, idx) ->
                        val p = ptrs.getOrNull(idx) ?: ptrs.first()
                        val ts = touchSpec
                        val sx: Float
                        val sy: Float
                        if (ts != null && ts.width > 0 && ts.height > 0 && p.x <= ts.width * 1.05f && p.y <= ts.height * 1.05f) {
                            val visW = (videoSpec.width - videoSpec.widthMargin).coerceAtLeast(1)
                            val visH = (videoSpec.height - videoSpec.heightMargin).coerceAtLeast(1)
                            sx = (videoSpec.widthMargin / 2f) + (p.x / ts.width.toFloat()) * visW
                            sy = (videoSpec.heightMargin / 2f) + (p.y / ts.height.toFloat()) * visH
                        } else {
                            sx = p.x
                            sy = p.y
                        }
                        val meAction = when (act) {
                            0 -> {
                                touchDownTime = SystemClock.uptimeMillis()
                                MotionEvent.ACTION_DOWN
                            }
                            1 -> MotionEvent.ACTION_UP
                            2 -> MotionEvent.ACTION_MOVE
                            5 -> MotionEvent.ACTION_DOWN
                            6 -> MotionEvent.ACTION_UP
                            else -> MotionEvent.ACTION_MOVE
                        }
                        engine?.injectTouch(sx, sy, meAction, touchDownTime.takeIf { it > 0L } ?: SystemClock.uptimeMillis(), SystemClock.uptimeMillis())
                    }
                }
            }
        }

        fun handleAapBuffer(buf: ByteArray, via: String): Int {
            var off = 0
            var consumed = 0
            while (off + 4 <= buf.size) {
                val channel = buf[off].toInt() and 0xFF
                val flags = buf[off + 1].toInt() and 0xFF
                val size = ((buf[off + 2].toInt() and 0xFF) shl 8) or (buf[off + 3].toInt() and 0xFF)
                if (size < 0 || size > 1_000_000) break
                if (buf.size - off < 4 + size) break
                val rawPayload = if (size > 0) buf.copyOfRange(off + 4, off + 4 + size) else ByteArray(0)
                val frameLen = 4 + size
                val enc = (flags and AaAapVersion.ENC_ENCRYPTED) != 0
                var msgId = -1
                var body = ByteArray(0)
                if (enc) {
                    try {
                        val plain = ssl.decryptApp(rawPayload)
                        if (plain.size >= 2) {
                            msgId = ((plain[0].toInt() and 0xFF) shl 8) or (plain[1].toInt() and 0xFF)
                            body = if (plain.size > 2) plain.copyOfRange(2, plain.size) else ByteArray(0)
                        } else {
                            off += frameLen
                            consumed = off
                            continue
                        }
                    } catch (e: Exception) {
                        log("POST-TLS $via decrypt fail ${e.message}")
                        off += frameLen
                        consumed = off
                        continue
                    }
                } else {
                    if (rawPayload.size < 2) {
                        off += frameLen
                        consumed = off
                        continue
                    }
                    msgId = ((rawPayload[0].toInt() and 0xFF) shl 8) or (rawPayload[1].toInt() and 0xFF)
                    body = if (rawPayload.size > 2) rawPayload.copyOfRange(2, rawPayload.size) else ByteArray(0)
                }
                handleMsg(channel, msgId, body, via)
                off += frameLen
                consumed = off
            }
            return consumed
        }

        val t0 = System.currentTimeMillis()
        val aapHold = ByteArrayOutputStream()
        try {
            while (System.currentTimeMillis() - t0 < maxDurationMs && io.isAlive()) {
                val chunk = io.waitDrain(100)
                if (chunk.isEmpty()) {
                    val now = System.currentTimeMillis()
                    if (sdSent == 0 && now - t0 > 1_500L) {
                        sendDiscovery("timeout-proactive")
                    }
                    if (!videoStarted && pendingStartIdx >= 0 && now >= waitConfigDeadlineMs) {
                        log("POST-TLS Video Config STATUS_WAIT timeout — starting idx=$pendingStartIdx")
                        beginVideoStream(pendingStartIdx)
                    }
                    continue
                }
                aapHold.write(chunk)
                val all = aapHold.toByteArray()
                val c = handleAapBuffer(all, "aap")
                if (c > 0) {
                    aapHold.reset()
                    if (c < all.size) aapHold.write(all, c, all.size - c)
                } else if (all.size > 64_000) {
                    aapHold.reset()
                }
            }
        } finally {
            runCatching { engine?.stop() }
        }
        return Result(discovery, if (videoCh > 0) videoCh else CH_VIDEO, rxFrames, notes.toString())
    }
}
