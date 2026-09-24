package com.maverick.mavemap.tracking

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssMeasurementsEvent
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import org.osmdroid.util.GeoPoint
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TrackingManager(
    context: Context
) : SensorEventListener {
    interface Listener {
        fun onTrackingChanged(tracking: Boolean, file: File?)
    }

    private val appContext = context.applicationContext
    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val writerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val storageManager = TrackStorageManager(appContext)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val lock = Any()
    private var writer: BufferedWriter? = null
    private var sessionStartNs = 0L
    private var sessionFile: File? = null
    private var latestLocation: Location? = null
    private var satellitesVisible = ""
    private var satellitesUsed = ""
    private var tracking = false
    private var listener: Listener? = null
    private var storageSession: TrackStorageManager.Session? = null
    private val trackPoints = mutableListOf<TrackPoint>()
    private var plannedRoute: PlannedRoute? = null

    private val values = mutableMapOf<Int, FloatArray>()
    private val accuracy = mutableMapOf<Int, Int>()
    private var rotationVector = ""
    private var gameRotationVector = ""
    private var yaw = ""
    private var pitch = ""
    private var roll = ""
    private var gnssMeasurementDetails = ""

    private val sensorTypes = intArrayOf(
        Sensor.TYPE_ACCELEROMETER,
        Sensor.TYPE_GYROSCOPE,
        Sensor.TYPE_GRAVITY,
        Sensor.TYPE_LINEAR_ACCELERATION,
        Sensor.TYPE_MAGNETIC_FIELD,
        Sensor.TYPE_ROTATION_VECTOR,
        Sensor.TYPE_GAME_ROTATION_VECTOR
    )

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            if (!tracking) return
            var visible = 0
            var used = 0
            for (index in 0 until status.satelliteCount) {
                visible++
                if (status.usedInFix(index)) used++
            }
            satellitesVisible = visible.toString()
            satellitesUsed = used.toString()
        }
    }

    private val measurementsCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        object : GnssMeasurementsEvent.Callback() {
            override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
                if (tracking) {
                    event.measurements.forEach { measurement ->
                        val fields = listOf(
                            "constellation=${measurement.constellationType}",
                            "svid=${measurement.svid}",
                            "carrier_hz=${if (measurement.hasCarrierFrequencyHz()) measurement.carrierFrequencyHz else ""}",
                            "cn0_dbhz=${measurement.cn0DbHz}",
                            "azimuth_deg=",
                            "elevation_deg=",
                            "state=${measurement.state}",
                            "multipath=${measurement.multipathIndicator}"
                        ).joinToString("|")
                        synchronized(lock) {
                            gnssMeasurementDetails = fields
                        }
                    }
                }
            }
        }
    } else {
        null
    }

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    val isTracking: Boolean
        @Synchronized get() = tracking

    fun setPlannedRoute(
        points: List<GeoPoint>,
        destinationName: String,
        destination: GeoPoint,
        distanceKm: Double,
        durationMinutes: Double
    ) {
        synchronized(lock) {
            plannedRoute = PlannedRoute(
                points.toList(), destinationName, destination, distanceKm, durationMinutes
            )
        }
    }

    fun start(): File? {
        synchronized(lock) {
            if (tracking) return null
            try {
                val sessionId = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                storageSession = storageManager.createSession(sessionId)
                writer = storageSession?.csv?.writer
                writer?.write(HEADER)
                writer?.newLine()
                writer?.flush()
            } catch (exception: Exception) {
                Log.e(TAG, "Unable to start tracking", exception)
                storageSession?.let { closeSessionFiles(it) }
                storageSession = null
                writer = null
                return null
            }
            sessionFile = File("Documents/Maverick/Tracks/${storageSession?.sessionId ?: ""}")
            trackPoints.clear()
            sessionStartNs = SystemClock.elapsedRealtimeNanos()
            tracking = true
            registerSensors()
            try {
                locationManager.registerGnssStatusCallback(gnssCallback)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && measurementsCallback != null) {
                    locationManager.registerGnssMeasurementsCallback(measurementsCallback)
                }
            } catch (exception: SecurityException) {
                Log.w(TAG, "GNSS callbacks unavailable", exception)
            }
            listener?.onTrackingChanged(true, sessionFile)
            return sessionFile
        }
    }

    fun stop() {
        synchronized(lock) {
            if (!tracking) return
            tracking = false
            unregisterSensors()
            try {
                locationManager.unregisterGnssStatusCallback(gnssCallback)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && measurementsCallback != null) {
                    locationManager.unregisterGnssMeasurementsCallback(measurementsCallback)
                }
            } catch (exception: Exception) {
                Log.w(TAG, "Unable to unregister GNSS callbacks", exception)
            }
            val closingWriter = writer
            val sessionToClose = storageSession
            val pointsToExport = trackPoints.toList()
            writer = null
            writerExecutor.submit {
                try {
                    closingWriter?.flush()
                    sessionToClose?.let { current ->
                        writeGeoJson(current.geoJson.writer, pointsToExport, current.sessionId)
                        writeGpx(current.gpx.writer, pointsToExport)
                        plannedRoute?.let { route ->
                            writePlannedRoute(current.plannedRoute.writer, route)
                        }
                        closeSessionFiles(current)
                    }
                } catch (exception: Exception) {
                    Log.e(TAG, "Unable to finalize tracking files", exception)
                }
            }
            storageSession = null
            listener?.onTrackingChanged(false, sessionFile)
        }
    }

    fun recordLocation(location: Location) {
        synchronized(lock) {
            if (!tracking || !location.latitude.isFinite() || !location.longitude.isFinite() || location.accuracy > 100f) return
            latestLocation = Location(location)
            val point = GeoPoint(location.latitude, location.longitude)
            val previous = trackPoints.lastOrNull()?.point
            if (previous == null || previous.distanceToAsDouble(point) <= 300.0) {
                trackPoints.add(TrackPoint(point, location.time, location.altitude))
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        synchronized(lock) {
            if (!tracking) return
            values[event.sensor.type] = event.values.copyOf()
            accuracy[event.sensor.type] = event.accuracy
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    rotationVector = event.values.csv(4)
                    updateOrientation(event.values)
                }
                Sensor.TYPE_GAME_ROTATION_VECTOR -> gameRotationVector = event.values.csv(4)
            }
            if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                enqueueSampleRow(buildSampleRow(event.timestamp))
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracyValue: Int) {
        synchronized(lock) {
            accuracy[sensor.type] = accuracyValue
        }
    }

    private fun updateOrientation(values: FloatArray) {
        val matrix = FloatArray(9)
        val orientation = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(matrix, values)
        SensorManager.getOrientation(matrix, orientation)
        yaw = Math.toDegrees(orientation[0].toDouble()).toFloat().toString()
        pitch = Math.toDegrees(orientation[1].toDouble()).toFloat().toString()
        roll = Math.toDegrees(orientation[2].toDouble()).toFloat().toString()
    }

    private fun registerSensors() {
        sensorTypes.forEach { type ->
            sensorManager.getDefaultSensor(type)?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    private fun unregisterSensors() {
        sensorManager.unregisterListener(this)
    }

    private fun enqueueSampleRow(line: String) {
        val targetWriter: BufferedWriter
        synchronized(lock) {
            targetWriter = writer ?: return
        }
        writerExecutor.submit {
            try {
                targetWriter.write(line)
                targetWriter.newLine()
            } catch (exception: Exception) {
                Log.e(TAG, "Unable to write tracking row", exception)
            }
        }
    }

    private fun buildSampleRow(eventTimestampNs: Long): String {
        val location = latestLocation
        val elapsed = if (sessionStartNs == 0L) "" else (eventTimestampNs - sessionStartNs).toString()
        val accel = values[Sensor.TYPE_ACCELEROMETER]
        val gyro = values[Sensor.TYPE_GYROSCOPE]
        val gravity = values[Sensor.TYPE_GRAVITY]
        val linear = values[Sensor.TYPE_LINEAR_ACCELERATION]
        val magnetic = values[Sensor.TYPE_MAGNETIC_FIELD]
        val rotation = values[Sensor.TYPE_ROTATION_VECTOR]
        val gameRotation = values[Sensor.TYPE_GAME_ROTATION_VECTOR]
        return listOf(
            dateFormat.format(Date()), elapsed, eventTimestampNs.toString(), "sample",
            location?.latitude.csv(), location?.longitude.csv(), location?.altitude.csv(),
            location?.speed.csv(), location?.bearing.csv(), location?.accuracy.csv(),
            location?.verticalAccuracy(), location?.speedAccuracy(), location?.bearingAccuracy(),
            location?.provider.orEmpty(), satellitesVisible, satellitesUsed,
            satellitesVisible, satellitesUsed, gnssMeasurementDetails,
            accel.component(0), accel.component(1), accel.component(2),
            gyro.component(0), gyro.component(1), gyro.component(2),
            gravity.component(0), gravity.component(1), gravity.component(2),
            linear.component(0), linear.component(1), linear.component(2),
            magnetic.component(0), magnetic.component(1), magnetic.component(2),
            rotation.component(0), rotation.component(1), rotation.component(2), rotation.component(3),
            gameRotation.component(0), gameRotation.component(1), gameRotation.component(2), gameRotation.component(3),
            yaw, pitch, roll, accuracy.values.maxOrNull()?.toString().orEmpty()
        ).joinToString(",") { quoteCsv(it.orEmpty()) }
    }

    private fun writeGeoJson(writer: BufferedWriter, points: List<TrackPoint>, sessionId: String) {
        val coordinates = points.joinToString(",") { "[${it.point.longitude},${it.point.latitude}]" }
        writer.write("{\"type\":\"Feature\",\"properties\":{\"session_id\":\"$sessionId\",\"source\":\"GNSS\"},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[$coordinates]}}")
        writer.newLine()
    }

    private fun writeGpx(writer: BufferedWriter, points: List<TrackPoint>) {
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"MAVEMap\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>MAVEMap GNSS track</name><trkseg>")
        points.forEach { point ->
            writer.write("<trkpt lat=\"${point.point.latitude}\" lon=\"${point.point.longitude}\">")
            if (point.altitude.isFinite()) writer.write("<ele>${point.altitude}</ele>")
            if (point.timeMs > 0L) writer.write("<time>${dateFormat.format(Date(point.timeMs))}</time>")
            writer.write("</trkpt>")
        }
        writer.write("</trkseg></trk></gpx>")
        writer.newLine()
    }

    private fun writePlannedRoute(writer: BufferedWriter, route: PlannedRoute) {
        val coordinates = route.points.joinToString(",") { "[${it.longitude},${it.latitude}]" }
        val name = route.destinationName.replace("\"", "\\\"")
        writer.write("{\"type\":\"Feature\",\"properties\":{\"destination_name\":\"$name\",\"destination_latitude\":${route.destination.latitude},\"destination_longitude\":${route.destination.longitude},\"distance_km\":${route.distanceKm},\"estimated_time_minutes\":${route.durationMinutes},\"source\":\"Valhalla\"},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[$coordinates]}}")
        writer.newLine()
    }

    private fun closeSessionFiles(session: TrackStorageManager.Session) {
        session.closePublishedFiles()
    }

    fun close() {
        stop()
        writerExecutor.shutdown()
    }

    private fun String?.csv(): String = this.orEmpty()
    private fun FloatArray?.csv(size: Int): String =
        if (this == null) "" else (0 until minOf(size, this.size)).joinToString("|") { this[it].toString() }
    private fun FloatArray?.component(index: Int): String =
        if (this != null && index < size) this[index].toString() else ""
    private fun Float?.csv(): String = this?.toString().orEmpty()
    private fun Double?.csv(): String = this?.toString().orEmpty()
    private fun Location?.verticalAccuracy(): String =
        if (this != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasVerticalAccuracy()) verticalAccuracyMeters.toString() else ""
    private fun Location?.speedAccuracy(): String =
        if (this != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasSpeedAccuracy()) speedAccuracyMetersPerSecond.toString() else ""
    private fun Location?.bearingAccuracy(): String =
        if (this != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasBearingAccuracy()) bearingAccuracyDegrees.toString() else ""
    private fun quoteCsv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    private fun BufferedWriter.closeQuietly() {
        try { close() } catch (_: Exception) { }
    }

    private data class PlannedRoute(
        val points: List<GeoPoint>,
        val destinationName: String,
        val destination: GeoPoint,
        val distanceKm: Double,
        val durationMinutes: Double
    )

    private data class TrackPoint(
        val point: GeoPoint,
        val timeMs: Long,
        val altitude: Double
    )

    companion object {
        private const val TAG = "TrackingManager"
        private const val HEADER = "timestamp_utc,elapsed_realtime_ns,event_timestamp_ns,event_type,latitude,longitude,altitude_m,speed_mps,bearing_deg,horizontal_accuracy_m,vertical_accuracy_m,speed_accuracy_mps,bearing_accuracy_deg,provider,satellites_visible,satellites_used,gnss_measurement,accelerometer_x,accelerometer_y,accelerometer_z,gyroscope_x,gyroscope_y,gyroscope_z,gravity_x,gravity_y,gravity_z,linear_acceleration_x,linear_acceleration_y,linear_acceleration_z,magnetic_field_x,magnetic_field_y,magnetic_field_z,rotation_vector_x,rotation_vector_y,rotation_vector_z,rotation_vector_w,game_rotation_vector_x,game_rotation_vector_y,game_rotation_vector_z,game_rotation_vector_w,yaw,pitch,roll,sensor_accuracy"
    }
}
