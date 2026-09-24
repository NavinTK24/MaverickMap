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

class DestinationSearch {

    data class Result(
        val name: String,
        val point: GeoPoint
    )

    fun search(
        query: String,
        onResult: (List<Result>) -> Unit
    ) {
        if (query.isBlank()) {
            onResult(emptyList())
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
                connection.setRequestProperty(
                    "User-Agent",
                    "MaveMap/1.0 Android"
                )

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val array = JSONArray(body)
                buildList {
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
            } catch (_: Exception) {
                emptyList()
            }

            withContext(Dispatchers.Main) {
                onResult(results)
            }
        }
    }
}
