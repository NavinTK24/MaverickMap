package com.maverick.mavemap

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.maverick.mavemap.compass.CompassManager
import com.maverick.mavemap.location.LocationManager
import com.maverick.mavemap.map.MapManager
import com.maverick.mavemap.map.MapRotation
import com.maverick.mavemap.navigation.DestinationSearch
import com.maverick.mavemap.navigation.NavigationManager
import com.maverick.mavemap.navigation.RouteManager
import com.maverick.mavemap.ui.NavigationPanel
import com.maverick.mavemap.ui.SearchDialog
import com.maverick.mavemap.compass.CompassView
import com.maverick.mavemap.tracking.TrackingManager
import com.maverick.mavemap.ui.theme.MAVEMapTheme
import org.osmdroid.config.Configuration
import org.osmdroid.views.MapView

class MainActivity : ComponentActivity(), SensorEventListener {

    private val mapPreferences by lazy {
        getSharedPreferences("mavemap_map_preferences", MODE_PRIVATE)
    }

    private lateinit var mapView: MapView
    private lateinit var mapManager: MapManager
    private lateinit var mapRotation: MapRotation
    private lateinit var compassManager: CompassManager
    private lateinit var locationManager: LocationManager
    private lateinit var destinationSearch: DestinationSearch
    private lateinit var routeManager: RouteManager
    private lateinit var navigationManager: NavigationManager
    private lateinit var trackingManager: TrackingManager
    private var gnssStateListener: ((Boolean) -> Unit)? = null
    private var composeCompassHeading by mutableFloatStateOf(0f)
    private var composeCompassDirection by mutableStateOf("N")
    private var lastCompassUiUpdateMs = 0L

    private val locationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                val enabled = locationManager.enable()
                gnssStateListener?.invoke(enabled)
                if (!enabled) {
                    Toast.makeText(this, "Enable location services on the device.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Location permission is required.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = "MAVEMap/1.0"

        mapView = MapView(this)

        mapManager = MapManager(this, mapView)
        val savedTheme = if (
            mapPreferences.getString("map_theme", MapManager.MapTheme.LIGHT.name) ==
            MapManager.MapTheme.DARK.name
        ) {
            MapManager.MapTheme.DARK
        } else {
            MapManager.MapTheme.LIGHT
        }
        mapManager.initialize(
            if (savedTheme == MapManager.MapTheme.DARK && mapManager.hasStadiaMapsApiKey) {
                savedTheme
            } else {
                MapManager.MapTheme.LIGHT
            }
        )

        mapRotation = MapRotation(mapView) {
            mapManager.updateMapRotation(it)
        }

        mapRotation.start()

        compassManager = CompassManager(this)
        locationManager = LocationManager(this)
        destinationSearch = DestinationSearch()
        routeManager = RouteManager()
        navigationManager = NavigationManager()
        trackingManager = TrackingManager(this)

        compassManager.initialize()
        compassManager.listener = object : CompassManager.Listener {
            override fun onHeadingChanged(heading: Float, direction: String) {
                val now = SystemClock.uptimeMillis()
                if (now - lastCompassUiUpdateMs < 33L) return
                lastCompassUiUpdateMs = now
                composeCompassHeading = heading
                composeCompassDirection = direction
                mapManager.updatePointer(
                    phoneHeading = heading,
                    targetHeading = mapManager.targetPointerHeading
                )
            }
        }

        locationManager.listener = object : LocationManager.Listener {
            override fun onLocationChanged(
                latitude: Double,
                longitude: Double,
                accuracy: Float,
                speed: Float
            ) {
                mapManager.updateLocation(latitude, longitude)
                navigationManager.updateCurrentLocation(latitude, longitude)
            }

            override fun onRawLocationChanged(location: Location) {
                trackingManager.recordLocation(location)
                if (trackingManager.isTracking) {
                    mapManager.appendTrackingLocation(
                        location.latitude,
                        location.longitude,
                        location.accuracy
                    )
                }
            }
        }

        compassManager.start()

        setContent {
            MAVEMapTheme {
                Box(Modifier.fillMaxSize()) {
                var searchResults by remember { mutableStateOf(emptyList<DestinationSearch.Result>()) }
                var searchMessage by remember { mutableStateOf<String?>(null) }
                var isSearching by remember { mutableStateOf(false) }
                var selectedDestination by remember { mutableStateOf<DestinationSearch.Result?>(null) }
                var routeInfo by remember { mutableStateOf<NavigationManager.RouteInfo?>(null) }
                var routeMessage by remember { mutableStateOf<String?>(null) }
                var gnssEnabled by remember { mutableStateOf(locationManager.isEnabled) }
                var tracking by remember { mutableStateOf(trackingManager.isTracking) }
                var mapTheme by remember { mutableStateOf(mapManager.mapTheme) }

                DisposableEffect(Unit) {
                    gnssStateListener = { gnssEnabled = it }
                    onDispose { gnssStateListener = null }
                }

                val calculateRoute: () -> Unit = {
                    val destination = selectedDestination
                    val start = locationManager.currentLocation
                    when {
                        destination == null -> routeMessage = "Search for and select a destination first."
                        !locationManager.isEnabled || start == null -> {
                            routeMessage = "Enable GPS to calculate a route."
                        }
                        else -> {
                            routeMessage = "Calculating route..."
                            routeManager.requestRoute(
                                start = start,
                                destination = destination.point
                            ) { route, error ->
                                if (route != null) {
                                    mapManager.showRoute(route.points)
                                    navigationManager.setRoute(route)
                                    selectedDestination?.let { destination ->
                                        trackingManager.setPlannedRoute(
                                            points = route.points,
                                            destinationName = destination.name,
                                            destination = destination.point,
                                            distanceKm = route.distanceKm,
                                            durationMinutes = route.durationMinutes
                                        )
                                    }
                                    routeInfo = navigationManager.routeInfo
                                    routeMessage = null
                                } else {
                                    routeMessage = error ?: "Route calculation failed."
                                }
                            }
                        }
                    }
                }

                androidx.compose.runtime.key(mapViewReadyKey()) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = {
                            mapView
                        }
                    )
                }

                CompassView(
                    heading = composeCompassHeading,
                    direction = composeCompassDirection,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .then(Modifier)
                )

                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 116.dp, end = 12.dp)
                        .clickable {
                            val nextTheme = if (mapTheme == MapManager.MapTheme.LIGHT) {
                                MapManager.MapTheme.DARK
                            } else {
                                MapManager.MapTheme.LIGHT
                            }
                            if (nextTheme == MapManager.MapTheme.DARK &&
                                !mapManager.hasStadiaMapsApiKey
                            ) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Configure stadiaMapsApiKey in local.properties, then rebuild.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                mapManager.setMapTheme(nextTheme)
                                mapTheme = nextTheme
                                mapPreferences.edit()
                                    .putString("map_theme", nextTheme.name)
                                    .apply()
                            }
                        }
                ) {
                    androidx.compose.material3.Text(
                        text = if (mapTheme == MapManager.MapTheme.LIGHT) "🌙" else "☀",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 22.sp
                    )
                }

                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                    SearchDialog(
                        modifier = Modifier.align(Alignment.TopStart),
                        onSearch = { query ->
                            if (query.isBlank()) {
                                isSearching = false
                                searchResults = emptyList()
                                searchMessage = null
                            } else {
                                isSearching = true
                                searchMessage = null
                                destinationSearch.search(query) { results, error ->
                                    isSearching = false
                                    searchResults = results
                                    searchMessage = error
                                }
                            }
                        },
                        results = searchResults,
                        onResultSelected = { result ->
                            searchResults = emptyList()
                            selectedDestination = result
                            routeInfo = null
                            routeMessage = null
                            mapManager.showDestination(result.point, result.name)
                            calculateRoute()
                        },
                        message = searchMessage,
                        isSearching = isSearching
                    )

                    NavigationPanel(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        gnssEnabled = gnssEnabled,
                        tracking = tracking,
                        onToggleGnss = {
                            if (locationManager.isEnabled) {
                                locationManager.disable()
                                gnssEnabled = false
                            } else {
                                requestLocationPermission()
                            }
                        },
                        onToggleTracking = {
                            if (trackingManager.isTracking) {
                                trackingManager.stop()
                                mapManager.stopTracking()
                                tracking = false
                            } else {
                                val file = trackingManager.start()
                                if (file == null) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Unable to create tracking file.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    mapManager.startTracking()
                                    tracking = true
                                }
                            }
                        },
                        onCalculateRoute = calculateRoute,
                        onClearRoute = {
                            mapManager.clearRoute()
                            navigationManager.clearRoute()
                            selectedDestination = null
                            routeInfo = null
                            routeMessage = null
                        },
                        routeInfo = routeInfo,
                        destinationName = selectedDestination?.name,
                        routeMessage = routeMessage
                    )
                }
            }
            }
        }
    }

    private fun mapViewReadyKey(): String = "map"

    private fun requestLocationPermission() {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fine || coarse) {
            val enabled = locationManager.enable()
            gnssStateListener?.invoke(enabled)
            if (!enabled) {
                Toast.makeText(this, "Enable location services on the device.", Toast.LENGTH_SHORT).show()
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (::mapRotation.isInitialized) mapRotation.start()
        if (::compassManager.isInitialized) compassManager.start()
    }

    override fun onPause() {
        if (::mapRotation.isInitialized) mapRotation.stop()
        if (::compassManager.isInitialized) compassManager.stop()
        super.onPause()
    }

    override fun onDestroy() {
        if (::mapRotation.isInitialized) mapRotation.stop()
        if (::compassManager.isInitialized) compassManager.stop()
        if (::trackingManager.isInitialized) trackingManager.close()
        if (::locationManager.isInitialized) locationManager.disable()
        super.onDestroy()
    }

    override fun onSensorChanged(event: android.hardware.SensorEvent?) {
        compassManager.onSensorChanged(event)
    }

    override fun onAccuracyChanged(
        sensor: android.hardware.Sensor?,
        accuracy: Int
    ) {
        compassManager.onAccuracyChanged(sensor, accuracy)
    }
}
