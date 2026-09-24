package com.maverick.mavemap.map

import android.content.Context
import androidx.core.content.ContextCompat
import com.maverick.mavemap.R
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

class MapManager(
    private val context: Context,
    private val mapView: MapView
) {
    private lateinit var locationMarker: HeadingArrowOverlay
    private var centered = false
    private var routePolyline: Polyline? = null

    var mapRotation: Float = 0f
        private set

    var targetPointerHeading: Float = 50f
        private set

    private var currentPhoneHeading = 0f
    private var currentLocation: GeoPoint? = null

    fun initialize() {
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(18.0)

        locationMarker = HeadingArrowOverlay(
            ContextCompat.getDrawable(
                context,
                R.drawable.ic_navigation_arrow
            )!!
        )
        mapView.overlays.add(locationMarker)
        updatePointer(currentPhoneHeading, targetPointerHeading)
    }

    fun updateLocation(latitude: Double, longitude: Double) {
        val point = GeoPoint(latitude, longitude)
        currentLocation = point
        locationMarker.setPosition(point)

        if (!centered) {
            mapView.controller.animateTo(point)
            centered = true
        }

        mapView.invalidate()
    }

    fun updatePointer(phoneHeading: Float, targetHeading: Float) {
        currentPhoneHeading = phoneHeading
        locationMarker.setHeading(
            normalizeHeading(targetHeading - phoneHeading - mapRotation)
        )
        mapView.invalidate()
    }

    fun setTargetPointerHeading(value: Float) {
        targetPointerHeading = normalizeHeading(value)
        updatePointer(currentPhoneHeading, targetPointerHeading)
    }

    fun updateMapRotation(rotation: Float) {
        mapRotation = normalizeHeading(rotation)
        updatePointer(currentPhoneHeading, targetPointerHeading)
    }

    fun showRoute(points: List<GeoPoint>) {
        routePolyline?.let { mapView.overlays.remove(it) }

        val polyline = Polyline(mapView)
        polyline.setPoints(points)
        routePolyline = polyline
        mapView.overlays.add(polyline)

        if (points.isNotEmpty()) {
            mapView.controller.setCenter(points[0])
        }
        mapView.invalidate()
    }

    fun clearRoute() {
        routePolyline?.let { mapView.overlays.remove(it) }
        routePolyline = null
        mapView.invalidate()
    }

    private fun normalizeHeading(value: Float): Float {
        var result = value % 360f
        if (result < 0f) result += 360f
        return result
    }
}
