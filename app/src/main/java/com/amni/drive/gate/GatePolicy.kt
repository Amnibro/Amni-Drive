package com.amni.drive.gate
data class GatePolicy(val gateDistracting: Boolean) {
    companion object {
        val GATED = GatePolicy(true)
        val UNGATED = GatePolicy(false)
    }
}
