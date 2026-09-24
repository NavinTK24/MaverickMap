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
import java.util.concurrent.TimeUnit
import android.util.Log

class RouteManager {

    data class Route(
        val points: List<GeoPoint>,
        val distanceKm: Double,
        val durationMinutes: Double
    )

    fun requestRoute(
        start: GeoPoint,
        destination: GeoPoint,
        onResult: (Route?, String?) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val route = try {
                val connection =
                    URL("https://valhalla1.openstreetmap.de/route")
                        .openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = TimeUnit.SECONDS.toMillis(20).toInt()
                connection.readTimeout = TimeUnit.SECONDS.toMillis(30).toInt()
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

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw IllegalStateException("Valhalla HTTP $responseCode")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d("RouteManager", "Valhalla response: $body")

                val json = try {
                    JSONObject(body)
                } catch (exception: Exception) {
                    throw RouteParseException(exception)
                }
                val tripData = try {
                    val trip = json.getJSONObject("trip")
                    val summary = trip.getJSONObject("summary")
                    val shape = trip.getJSONArray("legs")
                        .getJSONObject(0)
                        .get("shape")
                    summary to shape
                } catch (exception: Exception) {
                    throw RouteParseException(exception)
                }
                val summary = tripData.first
                val shape = tripData.second

                val points = try {
                    parseShape(shape)
                } catch (exception: Exception) {
                    throw RouteParseException(exception)
                }
                if (points.isEmpty()) {
                    throw RouteParseException(
                        IllegalStateException("Route contains no coordinates")
                    )
                }

                Route(
                    points = points,
                    distanceKm = summary.optDouble("length", 0.0),
                    durationMinutes = summary.optDouble("time", 0.0) / 60.0
                )
            } catch (exception: Exception) {
                Log.e("RouteManager", "Route request failed", exception)
                withContext(Dispatchers.Main) {
                    onResult(
                        null,
                        if (exception is RouteParseException) {
                            "Unable to parse route."
                        } else {
                            "Route calculation failed."
                        }
                    )
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                onResult(route, if (route.points.isEmpty()) "The routing server returned no route." else null)
            }
        }
    }

    private fun parseShape(shape: Any): List<GeoPoint> {
        return when (shape) {
            is JSONObject -> parseGeoJsonCoordinates(shape.getJSONArray("coordinates"))
            is String -> decodePolyline6(shape)
            else -> throw IllegalStateException("Unsupported route shape type")
        }
    }

    private fun parseGeoJsonCoordinates(coordinates: org.json.JSONArray): List<GeoPoint> {
        return buildList {
            for (i in 0 until coordinates.length()) {
                val pair = coordinates.getJSONArray(i)
                val longitude = pair.getDouble(0)
                val latitude = pair.getDouble(1)
                add(GeoPoint(latitude, longitude))
            }
        }
    }

    private fun decodePolyline6(encoded: String): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        var index = 0
        var latitude = 0
        var longitude = 0

        while (index < encoded.length) {
            val latitudeDelta = decodePolylineValue(encoded, index)
            index = latitudeDelta.nextIndex
            val longitudeDelta = decodePolylineValue(encoded, index)
            index = longitudeDelta.nextIndex

            latitude += latitudeDelta.value
            longitude += longitudeDelta.value
            points.add(
                GeoPoint(
                    latitude / 1_000_000.0,
                    longitude / 1_000_000.0
                )
            )
        }

        return points
    }

    private fun decodePolylineValue(encoded: String, startIndex: Int): DecodedValue {
        var index = startIndex
        var result = 0
        var shift = 0

        while (true) {
            if (index >= encoded.length || shift > 30) {
                throw IllegalStateException("Invalid encoded route shape")
            }

            val byte = encoded[index++].code - 63
            result = result or ((byte and 0x1f) shl shift)
            shift += 5
            if (byte < 0x20) break
        }

        val value = if ((result and 1) != 0) {
            -(result ushr 1) - 1
        } else {
            result ushr 1
        }
        return DecodedValue(value, index)
    }

    private data class DecodedValue(
        val value: Int,
        val nextIndex: Int
    )

    private class RouteParseException(cause: Throwable) :
        Exception("Unable to parse route", cause)
}
