package com.amni.drive.gate
import com.amni.drive.config.DriveConfig
import com.amni.drive.display.DisplayRole
object GateResolver {
    fun policyFor(role: DisplayRole, cfg: DriveConfig): GatePolicy = when (role) {
        DisplayRole.REAR_PASSENGER -> GatePolicy(cfg.rearGateEnabled)
        else -> GatePolicy(cfg.driverGateEnabled)
    }
    fun input(category: AppCategory, role: DisplayRole, state: DriveState, cfg: DriveConfig, passengerConfirmed: Boolean): GateInput =
        GateInput(category, policyFor(role, cfg), state, cfg.masterGateEnabled, passengerConfirmed)
}
