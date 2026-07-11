package com.amni.drive.gate
data class GateInput(
    val category: AppCategory,
    val policy: GatePolicy,
    val driveState: DriveState,
    val masterEnabled: Boolean,
    val passengerConfirmed: Boolean
)
object GateManager {
    fun decide(i: GateInput): GateDecision = when {
        !i.category.distracting -> allow("non-distracting category")
        !i.masterEnabled -> allow("gate master disabled")
        !i.policy.gateDistracting -> allow("display ungated")
        i.driveState.parked -> allow("vehicle parked")
        i.passengerConfirmed -> GateDecision(true, false, true, "passenger confirmed")
        else -> GateDecision(false, true, true, "gated: vehicle not parked")
    }
    private fun allow(reason: String) = GateDecision(true, false, false, reason)
}
