package com.amni.drive.gate.speed
import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
class GpsMotionSource(private val context: Context, private val intervalMs: Long = 1000L) : MotionSource {
    override val id = "gps"
    @SuppressLint("MissingPermission")
    override fun samples(): Flow<MotionSample> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs).setMinUpdateIntervalMillis(intervalMs).build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val speed = if (loc.hasSpeed()) loc.speed else null
                val acc = if (loc.hasSpeedAccuracy()) loc.speedAccuracyMetersPerSecond else null
                trySend(MotionSample(speed, acc, null, System.currentTimeMillis()))
            }
        }
        runCatching { client.requestLocationUpdates(request, cb, Looper.getMainLooper()) }
        awaitClose { client.removeLocationUpdates(cb) }
    }
}
