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
    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(r: LocationResult) { r.lastLocation?.let { _location.value = it } }
    }
    private val lmListener = object : android.location.LocationListener {
        override fun onLocationChanged(l: Location) { _location.value = l }
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
                fusedClient.requestLocationUpdates(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).build(), fusedCallback, Looper.getMainLooper())
            }
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, lmListener, Looper.getMainLooper())
            if (!hasGms && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 0f, lmListener, Looper.getMainLooper())
        }
    }
    fun stop() {
        runCatching { if (hasGms) fusedClient.removeLocationUpdates(fusedCallback) }
        runCatching { lm.removeUpdates(lmListener) }
    }
}
