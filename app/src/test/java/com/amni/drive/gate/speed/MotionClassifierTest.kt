package com.amni.drive.gate.speed
import com.amni.drive.gate.DriveState
import org.junit.Assert.assertEquals
import org.junit.Test
class MotionClassifierTest {
    private fun s(speed: Float?, acc: Float? = 1f, parked: Boolean? = null) = MotionSample(speed, acc, parked, 0L)
    @Test fun gearParkedIsParked() {
        assertEquals(DriveState.PARKED, MotionClassifier().update(s(null, parked = true)))
    }
    @Test fun singleFastSampleLocksToMovingImmediately() {
        assertEquals(DriveState.MOVING, MotionClassifier().update(s(10f)))
    }
    @Test fun threeStoppedSamplesRelaxToIdling() {
        val c = MotionClassifier()
        c.update(s(0f)); c.update(s(0f))
        assertEquals(DriveState.IDLING, c.update(s(0f)))
    }
    @Test fun nullSpeedIsUnknownNotParked() {
        assertEquals(DriveState.UNKNOWN, MotionClassifier().update(s(null)))
    }
    @Test fun badAccuracyFailsClosed() {
        val c = MotionClassifier(MotionConfig(maxAccuracyMps = 3f))
        c.update(s(10f))
        assertEquals(DriveState.UNKNOWN, c.update(s(0.5f, acc = 50f)))
    }
    @Test fun movingUnlocksOnlyAfterStopDebounce() {
        val c = MotionClassifier()
        c.update(s(10f))
        assertEquals(DriveState.MOVING, c.update(s(0f)))
        c.update(s(0f))
        assertEquals(DriveState.IDLING, c.update(s(0f)))
    }
}
