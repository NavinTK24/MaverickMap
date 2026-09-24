package com.maverick.mavemap.map

import android.content.Context
import androidx.core.content.ContextCompat
import com.maverick.mavemap.R
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import android.location.Location
import android.graphics.Color
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import com.maverick.mavemap.BuildConfig

class MapManager(
    private val context: Context,
    private val mapView: MapView
) {
    enum class MapTheme {
        LIGHT,
        DARK
    }

    private lateinit var locationMarker: HeadingArrowOverlay
    private var centered = false
    private var routePolyline: Polyline? = null
    private var destinationMarker: Marker? = null
    private var trackingPolyline: Polyline? = null
    private var lastTrackingPoint: GeoPoint? = null
    private var routePoints: List<GeoPoint> = emptyList()
    private var trackingPoints: List<GeoPoint> = emptyList()

    var mapTheme: MapTheme = MapTheme.LIGHT
        private set

    val hasStadiaMapsApiKey: Boolean
        get() = BuildConfig.STADIA_MAPS_API_KEY.isNotBlank()

    var mapRotation: Float = 0f
        private set

    var targetPointerHeading: Float = 50f
        private set

    private var currentPhoneHeading = 0f
    private var currentLocation: GeoPoint? = null
    private lateinit var rotationGestureOverlay: RotationGestureOverlay

    fun initialize(initialTheme: MapTheme = MapTheme.LIGHT) {
        mapTheme = initialTheme
        applyTileSource(initialTheme)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(18.0)

        rotationGestureOverlay = RotationGestureOverlay(mapView).apply {
            isEnabled = true
        }
        mapView.overlays.add(rotationGestureOverlay)

        locationMarker = HeadingArrowOverlay(
            ContextCompat.getDrawable(
                context,
                R.drawable.ic_navigation_arrow
            )!!
        )
        mapView.overlays.add(locationMarker)
        updatePointer(currentPhoneHeading, targetPointerHeading)
    }

    fun setMapTheme(theme: MapTheme) {
        if (theme == MapTheme.DARK && !hasStadiaMapsApiKey) return
        if (mapTheme == theme) return
        mapTheme = theme
        applyTileSource(theme)
        routePolyline?.color = routeColor(theme)
        trackingPolyline?.color = trackingColor(theme)
        mapView.invalidate()
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
        routePoints = points.toList()
        polyline.color = routeColor(mapTheme)
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
        routePoints = emptyList()
        mapView.invalidate()
    }

    fun startTracking() {
        trackingPolyline?.let { mapView.overlays.remove(it) }
        trackingPolyline = Polyline(mapView).apply {
            color = trackingColor(mapTheme)
            width = 8f
        }
        mapView.overlays.add(trackingPolyline)
        lastTrackingPoint = null
        trackingPoints = emptyList()
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
        trackingPoints = trackingPoints + point
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

    private fun applyTileSource(theme: MapTheme) {
        val tileSource = when (theme) {
            MapTheme.LIGHT -> XYTileSource(
                "OpenStreetMap Standard",
                0,
                19,
                256,
                ".png",
                arrayOf("https://tile.openstreetmap.org/")
            )
            MapTheme.DARK -> object : XYTileSource(
                "Stadia Alidade Smooth Dark",
                0,
                19,
                256,
                ".png",
                arrayOf("https://tiles.stadiamaps.com/tiles/alidade_smooth_dark/")
            ) {
                override fun getTileURLString(mapTileIndex: Long): String {
                    return super.getTileURLString(mapTileIndex) +
                        "?api_key=${BuildConfig.STADIA_MAPS_API_KEY}"
                }
            }
        }
        mapView.setTileSource(tileSource)
    }

    private fun routeColor(theme: MapTheme): Int = when (theme) {
        MapTheme.LIGHT -> Color.rgb(0, 92, 220)
        MapTheme.DARK -> Color.rgb(255, 193, 7)
    }

    private fun trackingColor(theme: MapTheme): Int = when (theme) {
        MapTheme.LIGHT -> Color.rgb(0, 125, 55)
        MapTheme.DARK -> Color.rgb(80, 220, 150)
    }
}
