package com.maverick.mavemap.map

import android.os.Handler
import android.os.Looper
import org.osmdroid.views.MapView
import kotlin.math.abs

class MapRotation(
    private val mapView: MapView,
    private val onRotationChanged: (Float) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var lastRotation = 0f

    private val runnable = object : Runnable {
        override fun run() {
            if (!running) return

            val rotation = normalize(mapView.mapOrientation)
            if (abs(smallestDifference(lastRotation, rotation)) > 0.05f) {
                lastRotation = rotation
                onRotationChanged(rotation)
            }

            handler.postDelayed(this, 30L)
        }
    }

    fun start() {
        if (running) return
        running = true
        handler.post(runnable)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(runnable)
    }

    private fun normalize(value: Float): Float {
        var result = value % 360f
        if (result < 0f) result += 360f
        return result
    }

    private fun smallestDifference(a: Float, b: Float): Float {
        var d = (b - a) % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }
}
