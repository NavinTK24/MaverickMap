package com.maverick.mavemap.tracking

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.charset.StandardCharsets

class TrackStorageManager(context: Context) {
    data class SessionFile(
        val name: String,
        val writer: BufferedWriter,
        val close: () -> Unit
    )

    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    fun createSession(sessionId: String): Session {
        val directory = "Documents/Maverick/Tracks/$sessionId/"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Session(
                sessionId = sessionId,
                createScopedFile("track.csv", "text/csv", directory),
                createScopedFile("track.geojson", "application/geo+json", directory),
                createScopedFile("track.gpx", "application/gpx+xml", directory),
                createScopedFile("planned_route.geojson", "application/geo+json", directory)
            )
        } else {
            val folder = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "Maverick/Tracks/$sessionId"
            )
            if (!folder.exists() && !folder.mkdirs()) {
                throw IllegalStateException("Unable to create public tracking directory")
            }
            Session(
                sessionId,
                createLegacyFile(File(folder, "track.csv")),
                createLegacyFile(File(folder, "track.geojson")),
                createLegacyFile(File(folder, "track.gpx")),
                createLegacyFile(File(folder, "planned_route.geojson"))
            )
        }
    }

    private fun createScopedFile(name: String, mimeType: String, directory: String): SessionFile {
        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, name)
            put(MediaStore.Files.FileColumns.MIME_TYPE, mimeType)
            put(MediaStore.Files.FileColumns.RELATIVE_PATH, directory)
            put(MediaStore.Files.FileColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values)
            ?: throw IllegalStateException("Unable to create $name")
        return try {
            val output = resolver.openOutputStream(uri, "w")
                ?: throw IllegalStateException("Unable to open $name")
            SessionFile(name, BufferedWriter(OutputStreamWriter(output, StandardCharsets.UTF_8))) {
                publish(uri)
            }
        } catch (exception: Exception) {
            resolver.delete(uri, null, null)
            throw exception
        }
    }

    private fun publish(uri: Uri) {
        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.IS_PENDING, 0)
        }
        resolver.update(uri, values, null, null)
    }

    private fun createLegacyFile(file: File): SessionFile {
        val output = FileOutputStream(file, false)
        return SessionFile(file.name, BufferedWriter(OutputStreamWriter(output, StandardCharsets.UTF_8))) {}
    }

    class Session(
        val sessionId: String,
        val csv: SessionFile,
        val geoJson: SessionFile,
        val gpx: SessionFile,
        val plannedRoute: SessionFile
    ) {
        fun closePublishedFiles() {
            listOf(csv, geoJson, gpx, plannedRoute).forEach { file ->
                file.writer.flush()
                file.writer.close()
                file.close()
            }
        }
    }
}
