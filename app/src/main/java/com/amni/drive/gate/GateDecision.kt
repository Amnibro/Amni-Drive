package com.amni.drive.gate
data class GateDecision(val allow: Boolean, val requiresConfirm: Boolean, val warn: Boolean, val reason: String)
