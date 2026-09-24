package com.maverick.mavemap.map

import android.graphics.Canvas
import android.graphics.Point
import android.graphics.drawable.Drawable
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

class HeadingArrowOverlay(
    private val arrowDrawable: Drawable
) : Overlay() {
    private val screenPoint = Point()
    private var position: GeoPoint? = null
    private var heading = 0f

    fun setPosition(newPosition: GeoPoint) {
        position = newPosition.clone()
    }

    fun setHeading(newHeading: Float) {
        heading = normalize(newHeading)
    }

    override fun draw(canvas: Canvas, projection: Projection) {
        if (!isEnabled) return

        val currentPosition = position ?: return
        projection.toPixels(currentPosition, screenPoint)

        val width = arrowDrawable.intrinsicWidth
        val height = arrowDrawable.intrinsicHeight
        if (width <= 0 || height <= 0) return

        val left = screenPoint.x - width / 2
        val top = screenPoint.y - height / 2
        val right = left + width
        val bottom = top + height

        canvas.save()
        canvas.rotate(
            normalize(heading),
            screenPoint.x.toFloat(),
            screenPoint.y.toFloat()
        )
        arrowDrawable.setBounds(left, top, right, bottom)
        arrowDrawable.draw(canvas)
        canvas.restore()
    }

    override fun onDetach(mapView: MapView) {
        arrowDrawable.setBounds(
            0, 0,
            arrowDrawable.intrinsicWidth,
            arrowDrawable.intrinsicHeight
        )
        super.onDetach(mapView)
    }

    private fun normalize(value: Float): Float {
        var result = value % 360f
        if (result < 0f) result += 360f
        return result
    }
}
