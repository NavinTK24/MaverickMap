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
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.maverick.mavemap.navigation.NavigationManager
import kotlin.math.roundToInt

@Composable
fun NavigationPanel(
    gpsEnabled: Boolean,
    heading: Float,
    targetHeading: Float,
    accuracy: Float,
    speed: Float,
    mapRotation: Float,
    compassStatus: String,
    onSetTarget: (Float) -> Unit,
    onEnableGps: () -> Unit,
    onDisableGps: () -> Unit,
    onClearRoute: () -> Unit,
    routeInfo: NavigationManager.RouteInfo?
) {
    var targetText by remember(targetHeading) {
        mutableStateOf(targetHeading.roundToInt().toString())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.94f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            if (gpsEnabled) {
                "GPS: ON • Target: ${targetHeading.roundToInt()}° • Phone: ${heading.roundToInt()}° • Accuracy: ${accuracy.roundToInt()} m"
            } else {
                "GPS: OFF • Target: ${targetHeading.roundToInt()}° • Phone: ${heading.roundToInt()}°"
            }
        )

        Text("Phone orientation: $compassStatus • Pointer target is fixed in world coordinates")
        Text("Map rotation: ${mapRotation.roundToInt()}°")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = targetText,
                onValueChange = { targetText = it.filter(Char::isDigit) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Road / target direction (°)") }
            )
            Button(
                onClick = {
                    targetText.toFloatOrNull()?.let(onSetTarget)
                }
            ) {
                Text("SET")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = onEnableGps
            ) {
                Text("ENABLE GPS")
            }

            Button(
                modifier = Modifier.weight(1f),
                onClick = onDisableGps,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.DarkGray
                )
            ) {
                Text("DISABLE GPS")
            }
        }

        routeInfo?.let {
            Text(
                "Route: %.2f km • %.0f min • Speed: %.1f m/s".format(
                    it.distanceKm,
                    it.durationMinutes,
                    speed
                )
            )
            Button(onClick = onClearRoute) {
                Text("CLEAR ROUTE")
            }
        }
    }
}
