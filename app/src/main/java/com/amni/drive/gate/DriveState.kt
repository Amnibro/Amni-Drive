package com.amni.drive.gate
enum class DriveState {
    PARKED, IDLING, MOVING, UNKNOWN;
    val parked: Boolean get() = this == PARKED
}
