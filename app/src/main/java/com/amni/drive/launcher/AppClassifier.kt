package com.amni.drive.launcher
import android.content.pm.ApplicationInfo
import com.amni.drive.gate.AppCategory
object AppClassifier {
    private val video = listOf("netflix", "youtube", "disney", "hulu", "hbo", ".max", "primevideo", "plex", "vlc", "twitch", "tiktok", "player", "kodi", "video")
    private val game = listOf("game", "genshin", "roblox", "pubg", "minecraft", "mojang", "supercell", "among")
    private val nav = listOf("maps", "waze", "navigation", "sygic", "tomtom", "here")
    private val media = listOf("spotify", "pandora", "soundcloud", "music", "audible", "podcast", "tunein", "iheart", "deezer")
    private val comms = listOf("whatsapp", "messenger", "telegram", "signal", "dialer", "incallui", ".phone", "mms", "messaging", "contacts")
    private val browser = listOf("chrome", "firefox", "browser", "opera", "brave", "duckduckgo", ".edge")
    fun classify(packageName: String, systemCategory: Int): AppCategory {
        val p = packageName.lowercase()
        return when {
            video.any { p.contains(it) } -> AppCategory.VIDEO
            game.any { p.contains(it) } -> AppCategory.GAME
            browser.any { p.contains(it) } -> AppCategory.INTERACTIVE
            nav.any { p.contains(it) } -> AppCategory.NAV
            media.any { p.contains(it) } -> AppCategory.MEDIA
            comms.any { p.contains(it) } -> AppCategory.COMMS
            systemCategory == ApplicationInfo.CATEGORY_VIDEO -> AppCategory.VIDEO
            systemCategory == ApplicationInfo.CATEGORY_GAME -> AppCategory.GAME
            systemCategory == ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.INTERACTIVE
            systemCategory == ApplicationInfo.CATEGORY_AUDIO -> AppCategory.MEDIA
            systemCategory == ApplicationInfo.CATEGORY_MAPS -> AppCategory.NAV
            else -> AppCategory.SAFE
        }
    }
}
