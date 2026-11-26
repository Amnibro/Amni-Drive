package com.amni.drive.map
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
class LocationTracker(context: Context) {
    private val app = context.applicationContext
    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location
    private val hasGms = runCatching { app.packageManager.getPackageInfo("com.google.android.gms", 0) }.isSuccess
    private val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(app) }
    @Volatile private var lastFusedAtMs: Long = 0L

    private fun accept(raw: Location, fromFused: Boolean) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (fromFused) lastFusedAtMs = now
        else if (hasGms && now - lastFusedAtMs < 4_500L) {
            // Avoid ping-ponging between Fused and raw GPS when Fused is actively delivering fixes.
            val prev = _location.value
            if (prev != null && (!raw.hasAccuracy() || raw.accuracy >= prev.accuracy * 0.85f)) return
        }
        val prev = _location.value
        if (prev == null) {
            _location.value = Location(raw)
            return
        }
        val acc = if (raw.hasAccuracy() && raw.accuracy > 0f) raw.accuracy else 15f
        val prevAcc = if (prev.hasAccuracy() && prev.accuracy > 0f) prev.accuracy else 15f
        // Reject sudden low-accuracy multipath jumps when we already have a crisp lock.
        if (acc > 45f && prevAcc <= 25f) return

        val dist = prev.distanceTo(raw)
        val spd = if (raw.hasSpeed()) raw.speed else -1f
        val deadbandM = maxOf(10f, acc * 0.75f).coerceAtMost(28f)
        val stationary = (spd in 0f..0.85f && dist < deadbandM) || (spd < 0f && dist < maxOf(8f, acc * 0.6f))
        if (stationary) {
            // Hold locked coordinates & bearing while parked so the map puck and compass never drift.
            val held = Location(prev).apply {
                time = raw.time
                speed = 0f
                if (acc < prevAcc) accuracy = acc
            }
            _location.value = held
            return
        }
        val clean = Location(raw)
        if (!clean.hasBearing() || (spd in 0f..1.4f)) {
            if (prev.hasBearing()) clean.bearing = prev.bearing
        }
        _location.value = clean
    }

    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(r: LocationResult) { r.lastLocation?.let { accept(it, fromFused = true) } }
    }
    private val lmListener = object : android.location.LocationListener {
        override fun onLocationChanged(l: Location) { accept(l, fromFused = false) }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }
    @SuppressLint("MissingPermission")
    fun start() {
        runCatching {
            (lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER))?.let { if (_location.value == null) _location.value = it }
            if (hasGms) {
                fusedClient.lastLocation.addOnSuccessListener { it?.let { l -> if (_location.value == null) _location.value = l } }
                fusedClient.requestLocationUpdates(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).setMinUpdateDistanceMeters(2f).build(), fusedCallback, Looper.getMainLooper())
            }
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 2f, lmListener, Looper.getMainLooper())
            if (!hasGms && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 5f, lmListener, Looper.getMainLooper())
        }
    }
    fun stop() {
        runCatching { if (hasGms) fusedClient.removeLocationUpdates(fusedCallback) }
        runCatching { lm.removeUpdates(lmListener) }
    }
}
