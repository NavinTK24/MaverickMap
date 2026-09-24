package com.maverick.mavemap.navigation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class RouteManager {

    data class Route(
        val points: List<GeoPoint>,
        val distanceKm: Double,
        val durationMinutes: Double
    )

    fun requestRoute(
        start: GeoPoint,
        destination: GeoPoint,
        onResult: (Route?) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val route = try {
                val connection =
                    URL("https://valhalla1.openstreetmap.de/route")
                        .openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")

                val payload = JSONObject()
                    .put(
                        "locations",
                        org.json.JSONArray()
                            .put(
                                JSONObject()
                                    .put("lat", start.latitude)
                                    .put("lon", start.longitude)
                            )
                            .put(
                                JSONObject()
                                    .put("lat", destination.latitude)
                                    .put("lon", destination.longitude)
                            )
                    )
                    .put("costing", "auto")
                    .put("units", "kilometers")
                    .put("shape_format", "geojson")

                OutputStreamWriter(connection.outputStream).use {
                    it.write(payload.toString())
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val json = JSONObject(body)
                val trip = json.getJSONObject("trip")
                val summary = trip.getJSONObject("summary")
                val shape = trip.getJSONObject("legs")
                    .getJSONArray(0)
                    .getJSONObject(0)
                    .getJSONObject("shape")

                val coordinates = shape.getJSONArray("coordinates")
                val points = buildList {
                    for (i in 0 until coordinates.length()) {
                        val pair = coordinates.getJSONArray(i)
                        add(GeoPoint(pair.getDouble(1), pair.getDouble(0)))
                    }
                }

                Route(
                    points = points,
                    distanceKm = summary.optDouble("length", 0.0),
                    durationMinutes = summary.optDouble("time", 0.0) / 60.0
                )
            } catch (_: Exception) {
                null
            }

            withContext(Dispatchers.Main) {
                onResult(route)
            }
        }
    }
}
