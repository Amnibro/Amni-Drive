package com.amni.drive.gate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class GateManagerTest {
    private fun input(cat: AppCategory, policyGated: Boolean, state: DriveState, master: Boolean = true, passenger: Boolean = false) =
        GateInput(cat, GatePolicy(policyGated), state, master, passenger)
    @Test fun nonDistractingAlwaysAllowed() {
        assertTrue(GateManager.decide(input(AppCategory.NAV, true, DriveState.MOVING)).allow)
        assertTrue(GateManager.decide(input(AppCategory.MEDIA, true, DriveState.MOVING)).allow)
        assertTrue(GateManager.decide(input(AppCategory.COMMS, true, DriveState.MOVING)).allow)
    }
    @Test fun rearUngatedAllowsVideoWhileMoving() {
        assertTrue(GateManager.decide(input(AppCategory.VIDEO, false, DriveState.MOVING)).allow)
    }
    @Test fun driverGatedBlocksVideoWhileMoving() {
        val d = GateManager.decide(input(AppCategory.VIDEO, true, DriveState.MOVING))
        assertFalse(d.allow)
        assertTrue(d.requiresConfirm)
    }
    @Test fun driverGatedAllowsVideoWhenParked() {
        assertTrue(GateManager.decide(input(AppCategory.VIDEO, true, DriveState.PARKED)).allow)
    }
    @Test fun driverGatedBlocksVideoWhenIdling() {
        assertFalse(GateManager.decide(input(AppCategory.VIDEO, true, DriveState.IDLING)).allow)
    }
    @Test fun passengerConfirmUnlocksWithWarning() {
        val d = GateManager.decide(input(AppCategory.GAME, true, DriveState.MOVING, passenger = true))
        assertTrue(d.allow)
        assertTrue(d.warn)
    }
    @Test fun masterOffAllowsEverything() {
        assertTrue(GateManager.decide(input(AppCategory.VIDEO, true, DriveState.MOVING, master = false)).allow)
    }
    @Test fun unknownStateStaysGatedFailClosed() {
        assertFalse(GateManager.decide(input(AppCategory.VIDEO, true, DriveState.UNKNOWN)).allow)
    }
}
