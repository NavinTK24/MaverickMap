package com.maverick.mavemap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.maverick.mavemap.navigation.NavigationManager

@Composable
fun NavigationPanel(
    modifier: Modifier = Modifier,
    gnssEnabled: Boolean,
    tracking: Boolean,
    onToggleGnss: () -> Unit,
    onToggleTracking: () -> Unit,
    onCalculateRoute: () -> Unit,
    onClearRoute: () -> Unit,
    routeInfo: NavigationManager.RouteInfo?,
    destinationName: String?,
    routeMessage: String?
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xE61A2028))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = onToggleGnss
            ) {
                Text(if (gnssEnabled) "DISABLE GNSS" else "ENABLE GNSS")
            }

            Button(
                modifier = Modifier.weight(1f),
                onClick = onToggleTracking,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tracking) Color(0xFF9B3D45) else Color(0xFF2B6F9F)
                )
            ) {
                Text(if (tracking) "STOP TRACKING" else "START TRACKING")
            }
        }

        routeInfo?.let {
            Text("Destination: ${destinationName ?: "Selected destination"}")
            Text("Distance: %.2f km".format(it.distanceKm))
            Text("Estimated time: %.0f min".format(it.durationMinutes))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCalculateRoute) {
                    Text("RECALCULATE")
                }
                Button(onClick = onClearRoute) {
                    Text("CLEAR ROUTE")
                }
            }
        } ?: destinationName?.let {
            Text("Destination: $it")
            Button(onClick = onCalculateRoute) { Text("CALCULATE ROUTE") }
        }

        routeMessage?.let { Text(it, color = Color(0xFFFFC857)) }
    }
}
