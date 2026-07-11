package com.amni.drive.gate.speed
import com.amni.drive.gate.DriveState
class MotionClassifier(private val cfg: MotionConfig = MotionConfig()) {
    private var moving = 0
    private var stopped = 0
    private var state = DriveState.UNKNOWN
    fun current(): DriveState = state
    fun update(s: MotionSample): DriveState {
        if (s.gearParked == true) { moving = 0; stopped = cfg.stoppedSamples; state = DriveState.PARKED; return state }
        val v = s.speedMps
        val badAccuracy = s.accuracyMps != null && s.accuracyMps > cfg.maxAccuracyMps
        if (v == null || badAccuracy) { state = if (state == DriveState.PARKED) DriveState.PARKED else DriveState.UNKNOWN; return state }
        state = if (v > cfg.movingThresholdMps) {
            moving++; stopped = 0; if (moving >= cfg.movingSamples) DriveState.MOVING else state
        } else {
            stopped++; moving = 0; if (stopped >= cfg.stoppedSamples) DriveState.IDLING else state
        }
        return state
    }
}
