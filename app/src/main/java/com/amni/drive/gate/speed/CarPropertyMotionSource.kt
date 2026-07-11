package com.amni.drive.gate.speed
import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.abs
class CarPropertyMotionSource(private val context: Context, private val intervalMs: Long = 1000L) : MotionSource {
    override val id = "car"
    override fun samples(): Flow<MotionSample> = flow {
        val mgr = runCatching { connect() }.getOrNull() ?: return@flow
        while (true) {
            val speed = readFloat(mgr, PERF_VEHICLE_SPEED)?.let { abs(it) }
            val brake = readBool(mgr, PARKING_BRAKE_ON)
            val gear = readInt(mgr, GEAR_SELECTION)
            val parked = brake == true || gear == GEAR_PARK
            emit(MotionSample(speed, 0f, if (parked) true else if (speed != null) false else null, System.currentTimeMillis()))
            delay(intervalMs)
        }
    }
    private fun connect(): Any {
        val carCls = Class.forName("android.car.Car")
        val car = carCls.getMethod("createCar", Context::class.java).invoke(null, context)
        return carCls.getMethod("getCarManager", String::class.java).invoke(car, "property")!!
    }
    private fun readFloat(mgr: Any, id: Int): Float? = runCatching {
        mgr.javaClass.getMethod("getFloatProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(mgr, id, 0) as Float
    }.getOrNull()
    private fun readInt(mgr: Any, id: Int): Int? = runCatching {
        mgr.javaClass.getMethod("getIntProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(mgr, id, 0) as Int
    }.getOrNull()
    private fun readBool(mgr: Any, id: Int): Boolean? = runCatching {
        mgr.javaClass.getMethod("getBooleanProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(mgr, id, 0) as Boolean
    }.getOrNull()
    companion object {
        const val PERF_VEHICLE_SPEED = 0x11600207
        const val GEAR_SELECTION = 0x11400400
        const val PARKING_BRAKE_ON = 0x11200402
        const val GEAR_PARK = 0x0004
        fun isAvailable(): Boolean = runCatching { Class.forName("android.car.Car") }.isSuccess
    }
}
