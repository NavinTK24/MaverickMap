package com.maverick.mavemap.compass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun CompassView(
    heading: Float,
    direction: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(125.dp, 115.dp)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f

            drawLine(
                Color.Black,
                Offset(centerX - 42f, centerY),
                Offset(centerX + 42f, centerY),
                3f,
                StrokeCap.Round
            )

            drawLine(
                Color.Black,
                Offset(centerX, centerY - 36f),
                Offset(centerX, centerY + 30f),
                3f,
                StrokeCap.Round
            )

            val phoneArrow = Path().apply {
                moveTo(centerX, centerY - 35f)
                lineTo(centerX - 9f, centerY - 15f)
                lineTo(centerX, centerY - 11f)
                lineTo(centerX + 9f, centerY - 15f)
                close()
            }
            drawPath(phoneArrow, Color.Black)

            val northRadius = 25f
            val northAngle = Math.toRadians((-heading).toDouble())
            val northX = centerX + sin(northAngle).toFloat() * northRadius
            val northY = centerY - cos(northAngle).toFloat() * northRadius

            drawLine(
                Color(0xFF00A000),
                Offset(centerX, centerY),
                Offset(northX, northY),
                4f,
                StrokeCap.Round
            )

            val arrowSize = 7f
            val leftAngle = northAngle + Math.toRadians(150.0)
            val rightAngle = northAngle - Math.toRadians(150.0)

            val leftX = northX + cos(leftAngle).toFloat() * arrowSize
            val leftY = northY + sin(leftAngle).toFloat() * arrowSize
            val rightX = northX + cos(rightAngle).toFloat() * arrowSize
            val rightY = northY + sin(rightAngle).toFloat() * arrowSize

            val northArrow = Path().apply {
                moveTo(northX, northY)
                lineTo(leftX, leftY)
                lineTo(rightX, rightY)
                close()
            }
            drawPath(northArrow, Color(0xFF00A000))

            drawCircle(Color.Black, 4f, Offset(centerX, centerY))
        }

        Text(
            direction,
            Modifier.align(Alignment.TopCenter).padding(top = 2.dp),
            Color.Black
        )

        Text(
            "${heading.roundToInt()}°",
            Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
            Color.Black
        )
    }
}
