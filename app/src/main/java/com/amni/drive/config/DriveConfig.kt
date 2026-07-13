package com.amni.drive.config
data class DriveConfig(
val masterGateEnabled: Boolean = false,
val driverGateEnabled: Boolean = false,
val rearGateEnabled: Boolean = false,
val movingThresholdMps: Float = 1.8f,
val passengerConfirmTimeoutMs: Long = 1800000L,
val autoRouteRearDisplay: Boolean = true,
val autoStartOnPower: Boolean = true,
val autoStartOnBluetooth: Boolean = false,
val carBtMac: String = "",
val autoEnterCarMode: Boolean = true,
val aaPathMode: String = PATH_RIDE,
val meshBridgeEnabled: Boolean = true,
val voiceNav: Boolean = true,
val msgBanner: Boolean = true,
val msgAutoRead: Boolean = false
) {
companion object {
const val PATH_RIDE = "ride"
const val PATH_OWN = "own"
const val PATH_AUTO = "auto"
fun normalizePath(v: String?): String = when (v?.lowercase()) {
PATH_OWN, "b", "wire", "host" -> PATH_OWN
PATH_AUTO, "auto" -> PATH_AUTO
PATH_RIDE, "a", "gearhead", null, "" -> PATH_RIDE
else -> PATH_RIDE
}
}
}
