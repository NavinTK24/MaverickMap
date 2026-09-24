package com.maverick.mavemap.map

import android.content.Context
import androidx.core.content.ContextCompat
import com.maverick.mavemap.R
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import android.location.Location

class MapManager(
    private val context: Context,
    private val mapView: MapView
) {
    private lateinit var locationMarker: HeadingArrowOverlay
    private var centered = false
    private var routePolyline: Polyline? = null
    private var destinationMarker: Marker? = null
    private var trackingPolyline: Polyline? = null
    private var lastTrackingPoint: GeoPoint? = null

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
            normalizeHeading(phoneHeading - mapRotation)
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
        polyline.color = ContextCompat.getColor(context, android.R.color.holo_blue_dark)
        polyline.width = 10f
        polyline.setPoints(points)
        routePolyline = polyline
        mapView.overlays.add(polyline)

        if (points.isNotEmpty()) {
            mapView.controller.setCenter(points[0])
        }
        mapView.invalidate()
    }

    fun showDestination(point: GeoPoint, title: String) {
        destinationMarker?.let { mapView.overlays.remove(it) }
        destinationMarker = Marker(mapView).apply {
            position = point
            this.title = title
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        mapView.overlays.add(destinationMarker)
        mapView.invalidate()
    }

    fun clearRoute() {
        routePolyline?.let { mapView.overlays.remove(it) }
        destinationMarker?.let { mapView.overlays.remove(it) }
        routePolyline = null
        destinationMarker = null
        mapView.invalidate()
    }

    fun startTracking() {
        trackingPolyline?.let { mapView.overlays.remove(it) }
        trackingPolyline = Polyline(mapView).apply {
            color = ContextCompat.getColor(context, android.R.color.holo_green_light)
            width = 8f
        }
        mapView.overlays.add(trackingPolyline)
        lastTrackingPoint = null
        mapView.invalidate()
    }

    fun appendTrackingLocation(latitude: Double, longitude: Double, accuracy: Float) {
        if (!latitude.isFinite() || !longitude.isFinite() || accuracy > 100f) return
        val point = GeoPoint(latitude, longitude)
        val previous = lastTrackingPoint
        if (previous != null) {
            val distance = FloatArray(1)
            Location.distanceBetween(
                previous.latitude, previous.longitude,
                latitude, longitude,
                distance
            )
            if (distance[0] > 300f) return
        }
        val polyline = trackingPolyline ?: return
        polyline.addPoint(point)
        lastTrackingPoint = point
        mapView.invalidate()
    }

    fun stopTracking() {
        lastTrackingPoint = null
        mapView.invalidate()
    }

    private fun normalizeHeading(value: Float): Float {
        var result = value % 360f
        if (result < 0f) result += 360f
        return result
    }
}
