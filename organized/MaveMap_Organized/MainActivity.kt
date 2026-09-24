package com.maverick.mavemap

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import org.osmdroid.config.Configuration
import org.osmdroid.views.MapView

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var mapView: MapView
    private lateinit var mapManager: MapManager
    private lateinit var mapRotation: MapRotation
    private lateinit var compassManager: CompassManager
    private lateinit var locationManager: LocationManager
    private lateinit var destinationSearch: DestinationSearch
    private lateinit var routeManager: RouteManager
    private lateinit var navigationManager: NavigationManager

    private var searchText = ""
    private var searchResults = emptyList<DestinationSearch.Result>()

    private val locationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) locationManager.enable()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )

        compassManager = CompassManager(this)
        locationManager = LocationManager(this)
        destinationSearch = DestinationSearch()
        routeManager = RouteManager()
        navigationManager = NavigationManager()

        compassManager.initialize()
        compassManager.listener = object : CompassManager.Listener {
            override fun onHeadingChanged(heading: Float, direction: String) {
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
        }

        compassManager.start()

        setContent {
            Box(Modifier.fillMaxSize()) {
                androidx.compose.runtime.key(mapViewReadyKey()) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            mapView = MapView(context)
                            mapManager = MapManager(context, mapView)
                            mapRotation = MapRotation(mapView) {
                                mapManager.updateMapRotation(it)
                            }
                            mapRotation.start()
                            mapManager.initialize()
                            mapView
                        }
                    )
                }

                CompassView(
                    heading = if (::compassManager.isInitialized) compassManager.heading else 0f,
                    direction = if (::compassManager.isInitialized) compassManager.direction else "N",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .then(Modifier)
                )

                SearchDialog(
                    onSearch = { query ->
                        destinationSearch.search(query) { results ->
                            searchResults = results
                        }
                    },
                    results = searchResults,
                    onResultSelected = { result ->
                        searchResults = emptyList()
                        locationManager.currentLocation?.let { start ->
                            routeManager.requestRoute(
                                start = start,
                                destination = result.point
                            ) { route ->
                                if (route != null) {
                                    mapManager.showRoute(route.points)
                                    navigationManager.setRoute(route)
                                }
                            }
                        }
                    }
                )

                NavigationPanel(
                    gpsEnabled = locationManager.isEnabled,
                    heading = compassManager.heading,
                    targetHeading = mapManager.targetPointerHeading,
                    accuracy = locationManager.accuracy,
                    speed = locationManager.speed,
                    mapRotation = mapManager.mapRotation,
                    compassStatus = compassManager.status,
                    onSetTarget = { value ->
                        mapManager.setTargetPointerHeading(value)
                    },
                    onEnableGps = { requestLocationPermission() },
                    onDisableGps = { locationManager.disable() },
                    onClearRoute = {
                        mapManager.clearRoute()
                        navigationManager.clearRoute()
                    },
                    routeInfo = navigationManager.routeInfo
                )
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
            locationManager.enable()
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
