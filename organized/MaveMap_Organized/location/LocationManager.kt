package com.maverick.mavemap.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import androidx.core.content.ContextCompat
import org.osmdroid.util.GeoPoint

class LocationManager(
    private val context: Context
) {
    interface Listener {
        fun onLocationChanged(
            latitude: Double,
            longitude: Double,
            accuracy: Float,
            speed: Float
        )
    }

    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    var listener: Listener? = null
    var currentLocation: GeoPoint? = null
        private set
    var accuracy: Float = 0f
        private set
    var speed: Float = 0f
        private set
    var isEnabled: Boolean = false
        private set

    private val request =
        LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L
        ).setMinUpdateIntervalMillis(500L).build()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            currentLocation = GeoPoint(location.latitude, location.longitude)
            accuracy = location.accuracy
            speed = location.speed

            listener?.onLocationChanged(
                location.latitude,
                location.longitude,
                location.accuracy,
                location.speed
            )
        }
    }

    fun enable() {
        if (!hasPermission()) return
        client.requestLocationUpdates(request, callback, context.mainLooper)
        isEnabled = true
    }

    fun disable() {
        client.removeLocationUpdates(callback)
        isEnabled = false
    }

    private fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
