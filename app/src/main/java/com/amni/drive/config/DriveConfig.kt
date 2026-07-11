package com.amni.drive.config
data class DriveConfig(
    val masterGateEnabled: Boolean = true,
    val driverGateEnabled: Boolean = true,
    val rearGateEnabled: Boolean = false,
    val movingThresholdMps: Float = 1.8f,
    val passengerConfirmTimeoutMs: Long = 1800000L,
    val autoRouteRearDisplay: Boolean = true
)
