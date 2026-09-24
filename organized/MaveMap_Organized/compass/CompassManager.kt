package com.maverick.mavemap.compass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import kotlin.math.roundToInt

class CompassManager(
    context: Context
) : SensorEventListener {

    interface Listener {
        fun onHeadingChanged(heading: Float, direction: String)
    }

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private var rotationVectorSensor: Sensor? = null
    private var gravitySensor: Sensor? = null
    private var magneticSensor: Sensor? = null
    private var gyroSensor: Sensor? = null

    private val rotationVectorValues = FloatArray(5)
    private val gravityValues = FloatArray(3)
    private val magneticValues = FloatArray(3)

    private var hasRotationVector = false
    private var hasGravity = false
    private var hasMagnetic = false

    var heading = 0f
        private set

    var direction = "N"
        private set

    var status = "CALIBRATING"
        private set

    var listener: Listener? = null

    fun initialize() {
        rotationVectorSensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        gravitySensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        magneticSensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        gyroSensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

    fun start() {
        rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gravitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        magneticSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val count = minOf(event.values.size, rotationVectorValues.size)
                System.arraycopy(event.values, 0, rotationVectorValues, 0, count)
                hasRotationVector = true
                calculateHeading()
            }

            Sensor.TYPE_GRAVITY -> {
                System.arraycopy(event.values, 0, gravityValues, 0, 3)
                hasGravity = true
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magneticValues, 0, 3)
                hasMagnetic = true
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun calculateHeading() {
        if (!hasRotationVector) return

        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(
            rotationMatrix,
            rotationVectorValues
        )

        val remapped = FloatArray(9)
        SensorManager.remapCoordinateSystem(
            rotationMatrix,
            SensorManager.AXIS_X,
            SensorManager.AXIS_Z,
            remapped
        )

        val orientation = FloatArray(3)
        SensorManager.getOrientation(remapped, orientation)

        var degrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
        degrees = normalize(degrees)

        heading = degrees
        direction = getCardinalDirection(heading)
        status = "READY"
        listener?.onHeadingChanged(heading, direction)
    }

    private fun normalize(value: Float): Float {
        var result = value % 360f
        if (result < 0f) result += 360f
        return result
    }

    private fun getCardinalDirection(value: Float): String {
        val directions = arrayOf(
            "N", "NE", "E", "SE",
            "S", "SW", "W", "NW"
        )
        return directions[
            (((value + 22.5f) / 45f).toInt()) % 8
        ]
    }
}
