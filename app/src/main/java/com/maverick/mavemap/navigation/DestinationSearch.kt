package com.maverick.mavemap.navigation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class DestinationSearch {

    data class Result(
        val name: String,
        val point: GeoPoint
    )

    fun search(
        query: String,
        onResult: (List<Result>, String?) -> Unit
    ) {
        if (query.isBlank()) {
            onResult(emptyList(), "Enter a destination to search.")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val results = try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = URL(
                    "https://nominatim.openstreetmap.org/search" +
                    "?q=$encoded&format=jsonv2&limit=5"
                )

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = TimeUnit.SECONDS.toMillis(15).toInt()
                connection.readTimeout = TimeUnit.SECONDS.toMillis(15).toInt()
                connection.setRequestProperty(
                    "User-Agent",
                    "MaveMap/1.0 Android"
                )

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw IllegalStateException("Nominatim HTTP $responseCode")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }

                val array = JSONArray(body)
                buildList<Result> {
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        add(
                            Result(
                                name = item.optString("display_name"),
                                point = GeoPoint(
                                    item.getDouble("lat"),
                                    item.getDouble("lon")
                                )
                            )
                        )
                    }
                }
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(emptyList(), exception.message ?: "Destination search failed.")
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                onResult(results, if (results.isEmpty()) "No destinations found." else null)
            }
        }
    }
}
