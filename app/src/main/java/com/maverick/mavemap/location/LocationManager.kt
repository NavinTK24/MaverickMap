package com.maverick.mavemap.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.location.LocationManager as AndroidLocationManager
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

        fun onRawLocationChanged(location: Location) = Unit
    }

    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val systemLocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as AndroidLocationManager

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
            listener?.onRawLocationChanged(location)
        }
    }

    fun enable(): Boolean {
        if (!hasPermission() || !isLocationServiceEnabled()) return false
        return try {
            client.requestLocationUpdates(request, callback, context.mainLooper)
            isEnabled = true
            true
        } catch (_: SecurityException) {
            false
        }
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

    private fun isLocationServiceEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            systemLocationManager.isLocationEnabled
        } else {
            systemLocationManager.isProviderEnabled(AndroidLocationManager.GPS_PROVIDER) ||
                systemLocationManager.isProviderEnabled(AndroidLocationManager.NETWORK_PROVIDER)
        }
    }
}
