package com.amni.drive.gate.speed
import kotlinx.coroutines.flow.Flow
interface MotionSource {
    val id: String
    fun samples(): Flow<MotionSample>
}
