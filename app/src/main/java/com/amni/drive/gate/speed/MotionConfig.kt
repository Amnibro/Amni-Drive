package com.amni.drive.gate.speed
data class MotionConfig(
    val movingThresholdMps: Float = 1.8f,
    val movingSamples: Int = 1,
    val stoppedSamples: Int = 3,
    val maxAccuracyMps: Float = 5f
)
