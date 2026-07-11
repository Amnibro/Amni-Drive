package com.amni.drive.gate
enum class AppCategory {
    VIDEO, GAME, INTERACTIVE, NAV, MEDIA, COMMS, SAFE;
    val distracting: Boolean get() = this == VIDEO || this == GAME || this == INTERACTIVE
}
