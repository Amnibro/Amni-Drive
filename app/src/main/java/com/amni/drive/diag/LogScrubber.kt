package com.amni.drive.diag
object LogScrubber {
    const val SUPPORT_EMAIL = "anthony@amni-scient.com"
    private val EMAIL_RE = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val KEYED_COORD_RE = Regex("(?i)\\b(lat|lon|lng|latitude|longitude|mlat|mlon|latE7|lonE7)\\s*([=:])\\s*[-+]?\\d+(?:\\.\\d+)?")
    private val PAIR_COORD_RE = Regex("(?<!\\d)[-+]?\\d{1,2}\\.\\d{3,9}\\s*,\\s*[-+]?\\d{1,3}\\.\\d{3,9}(?!\\d)")
    private val QUOTED_QUERY_RE = Regex("(?i)(Searching|Searched|Routing|Heard:|Sent)\\s+[“\"'][^”\"'\\n]*[”\"']")
    private val ROUTING_TO_RE = Regex("(?i)Routing to [^\\n]+")
    private val MSG_FROM_RE = Regex("(?i)Message from [^\\n]+")
    private val SECRET_KV_RE = Regex("(?i)\\b(q|query|search_query|term|addr|address|dest|destination|pass|token|cookie|set-cookie|authorization|bearer|apikey|api_key|ssid)\\s*([=:])\\s*(\"[^\"]*\"|'[^']*'|[^\\s&;\\n\"']+)")
    private val WIFI_LABEL_RE = Regex("(?i)Wi-Fi:\\s*[^\\n·]+")
    private val MAC_RE = Regex("\\b(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}\\b")
    private val IPV4_RE = Regex("\\b(?!127\\.0\\.0\\.1\\b|0\\.0\\.0\\.0\\b)(?:\\d{1,3}\\.){3}\\d{1,3}\\b")
    private val PHONE_RE = Regex("(?<!\\d)(?:\\+?1[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]\\d{3}[-.\\s]\\d{4}(?!\\d)")
    private val USER_PATH_RE = Regex("(?:/storage/emulated/\\d+|/sdcard)/[^\\s\"')]+")
    fun scrub(raw: String): String {
        if (raw.isEmpty()) return raw
        var s = raw
        s = EMAIL_RE.replace(s) { m -> if (m.value.equals(SUPPORT_EMAIL, ignoreCase = true)) SUPPORT_EMAIL else "<EMAIL>" }
        s = KEYED_COORD_RE.replace(s) { m -> "${m.groupValues[1]}${m.groupValues[2]}<COORD>" }
        s = PAIR_COORD_RE.replace(s, "<COORD>,<COORD>")
        s = QUOTED_QUERY_RE.replace(s) { m -> "${m.groupValues[1]} \"<REDACTED>\"" }
        s = ROUTING_TO_RE.replace(s, "Routing to <REDACTED>")
        s = MSG_FROM_RE.replace(s, "Message from <REDACTED>")
        s = SECRET_KV_RE.replace(s) { m -> "${m.groupValues[1]}${m.groupValues[2]}<REDACTED>" }
        s = WIFI_LABEL_RE.replace(s, "Wi-Fi: <SSID>")
        s = MAC_RE.replace(s, "<MAC>")
        s = IPV4_RE.replace(s, "<IP>")
        s = PHONE_RE.replace(s, "<PHONE>")
        s = USER_PATH_RE.replace(s, "<STORAGE_PATH>")
        return s
    }
}
