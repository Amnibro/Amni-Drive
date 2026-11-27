package com.amni.drive.map
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.amni.drive.launcher.CarEmbed
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
    @Volatile private var started: Boolean = false

    private fun accept(raw: Location, fromFused: Boolean) {
        if (raw.latitude.isNaN() || raw.longitude.isNaN()) return
        if (kotlin.math.abs(raw.latitude) < 1e-5 && kotlin.math.abs(raw.longitude) < 1e-5) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (fromFused) lastFusedAtMs = now
        else if (hasGms && now - lastFusedAtMs < 4_500L) {
            val prev = _location.value
            if (prev != null && (!raw.hasAccuracy() || raw.accuracy >= prev.accuracy * 0.85f)) return
        }
        val prev = _location.value
        if (prev == null) {
            val first = Location(raw)
            _location.value = first
            CarEmbed.sendLocation(first)
            Log.i(TAG, "first fix provider=${first.provider} acc=${first.accuracy}m")
            return
        }
        val acc = if (raw.hasAccuracy() && raw.accuracy > 0f) raw.accuracy else 15f
        val prevAcc = if (prev.hasAccuracy() && prev.accuracy > 0f) prev.accuracy else 15f
        if (acc > 60f && prevAcc <= 25f && now - lastFusedAtMs < 15_000L) return

        val dist = prev.distanceTo(raw)
        val dtS = ((raw.time - prev.time) / 1000f).coerceIn(0.25f, 15f)
        val derivedSpd = dist / dtS
        val spd = if (raw.hasSpeed()) raw.speed else -1f
        val deadbandM = maxOf(2.5f, acc * 0.35f).coerceAtMost(7.5f)
        val stationary = (spd < 0f || spd < 0.75f) && derivedSpd < 1.1f && dist < deadbandM
        if (stationary) {
            val held = Location(prev).apply {
                time = raw.time
                speed = 0f
                if (acc < prevAcc) accuracy = acc
            }
            _location.value = held
            CarEmbed.sendLocation(held)
            return
        }
        val clean = Location(raw)
        if (!clean.hasSpeed() && derivedSpd >= 0.8f) clean.speed = derivedSpd
        if ((!clean.hasBearing() || clean.bearing == 0f || spd in 0f..1.1f) && dist >= 2.5f) {
            clean.bearing = ((prev.bearingTo(clean) % 360f) + 360f) % 360f
        } else if (!clean.hasBearing() && prev.hasBearing()) {
            clean.bearing = prev.bearing
        }
        _location.value = clean
        CarEmbed.sendLocation(clean)
    }

    /** Feeds the head unit's rooftop GPS sensor (AAP SENSOR_LOCATION = 1) directly into the tracker. */
    fun injectCarLocation(
        lat: Double,
        lon: Double,
        accuracyM: Float? = null,
        speedMps: Float? = null,
        bearingDeg: Float? = null,
        altitudeM: Double? = null
    ) {
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return
        if (kotlin.math.abs(lat) < 1e-5 && kotlin.math.abs(lon) < 1e-5) return
        val loc = Location("car-aap").apply {
            latitude = lat
            longitude = lon
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
            accuracy = (accuracyM?.takeIf { it > 0f } ?: 6f)
            speedMps?.takeIf { it >= 0f }?.let { speed = it }
            bearingDeg?.takeIf { it in 0f..360f }?.let { bearing = it }
            altitudeM?.let { altitude = it }
        }
        accept(loc, fromFused = true)
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
        if (!hasLocationPermission(app)) {
            Log.w(TAG, "start() skipped phone GPS — location permission not granted yet (car AAP GPS still active)")
            return
        }
        runCatching {
            val providers = buildList {
                add(LocationManager.GPS_PROVIDER)
                add(LocationManager.NETWORK_PROVIDER)
                add(LocationManager.PASSIVE_PROVIDER)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            }
            providers.mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                .maxByOrNull { it.time }
                ?.let { best -> if (_location.value == null) accept(best, fromFused = false) }

            if (hasGms) {
                fusedClient.lastLocation.addOnSuccessListener { it?.let { l -> if (_location.value == null) accept(l, fromFused = true) } }
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { it?.let { l -> accept(l, fromFused = true) } }
                if (!started) {
                    val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                        .setMinUpdateIntervalMillis(500L)
                        .setMinUpdateDistanceMeters(0f)
                        .setWaitForAccurateLocation(false)
                        .build()
                    fusedClient.requestLocationUpdates(req, fusedCallback, Looper.getMainLooper())
                }
            }
            if (!started) {
                if (runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)) {
                    runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, lmListener, Looper.getMainLooper()) }
                }
                if (runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) {
                    runCatching { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 0f, lmListener, Looper.getMainLooper()) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && runCatching { lm.isProviderEnabled(LocationManager.FUSED_PROVIDER) }.getOrDefault(false)) {
                    runCatching { lm.requestLocationUpdates(LocationManager.FUSED_PROVIDER, 1000L, 0f, lmListener, Looper.getMainLooper()) }
                }
                started = true
            }
        }.onFailure { Log.w(TAG, "start err: ${it.message}") }
    }

    fun stop() {
        started = false
        runCatching { if (hasGms) fusedClient.removeLocationUpdates(fusedCallback) }
        runCatching { lm.removeUpdates(lmListener) }
    }

    companion object {
        private const val TAG = "Amni.Loc"
        fun hasLocationPermission(ctx: Context): Boolean =
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}
