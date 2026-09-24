package com.maverick.mavemap.navigation

import org.osmdroid.util.GeoPoint

class NavigationManager {

    data class RouteInfo(
        val distanceKm: Double,
        val durationMinutes: Double
    )

    var currentLocation: GeoPoint? = null
        private set

    var route: RouteManager.Route? = null
        private set

    val routeInfo: RouteInfo?
        get() = route?.let {
            RouteInfo(it.distanceKm, it.durationMinutes)
        }

    fun updateCurrentLocation(latitude: Double, longitude: Double) {
        currentLocation = GeoPoint(latitude, longitude)
    }

    fun setRoute(newRoute: RouteManager.Route) {
        route = newRoute
    }

    fun clearRoute() {
        route = null
    }
}
