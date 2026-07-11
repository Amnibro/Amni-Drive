package com.amni.drive.gate.speed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
class ManualMotionSource : MotionSource {
    override val id = "manual"
    private val parked = MutableStateFlow(false)
    fun setParked(value: Boolean) { parked.value = value }
    override fun samples(): Flow<MotionSample> = parked.map { MotionSample(if (it) 0f else null, 0f, if (it) true else null, System.currentTimeMillis()) }
}
