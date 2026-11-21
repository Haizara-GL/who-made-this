package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalVestnikColors
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun StarOfDavidLoader(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color = LocalVestnikColors.current.amberAlert,
    strokeWidth: Dp = 3.dp,
    label: String? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "star_of_david_loader")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "star_rotation"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "star_pulse"
    )

    Column(
        modifier = modifier
            .semantics { contentDescription = label ?: "Загрузка" }
            .testTag("star_of_david_loader"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val strokePx = strokeWidth.toPx()
            val radius = ((this.size.minDimension / 2f) - strokePx * 1.4f).coerceAtLeast(2f) * pulse

            // Subtle outer ring for visual balance on larger loaders
            if (this.size.minDimension >= 36.dp.toPx()) {
                drawCircle(
                    color = color.copy(alpha = 0.14f),
                    radius = (this.size.minDimension / 2f) - strokePx * 0.6f,
                    center = Offset(cx, cy),
                    style = Stroke(width = (strokePx * 0.6f).coerceAtLeast(1f))
                )
            }

            rotate(degrees = rotation, pivot = Offset(cx, cy)) {
                val strokeStyle = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )

                // Upward equilateral triangle (-90°, 30°, 150°)
                val upTriangle = buildEquilateralTrianglePath(
                    cx = cx,
                    cy = cy,
                    radius = radius,
                    startAngleDeg = -90.0
                )
                // Downward equilateral triangle (90°, 210°, 330°)
                val downTriangle = buildEquilateralTrianglePath(
                    cx = cx,
                    cy = cy,
                    radius = radius,
                    startAngleDeg = 90.0
                )

                drawPath(path = upTriangle, color = color, style = strokeStyle)
                drawPath(path = downTriangle, color = accentColor, style = strokeStyle)
            }
        }

        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun buildEquilateralTrianglePath(
    cx: Float,
    cy: Float,
    radius: Float,
    startAngleDeg: Double
): Path = Path().apply {
    for (i in 0 until 3) {
        val angleRad = Math.toRadians(startAngleDeg + i * 120.0)
        val x = cx + (radius * cos(angleRad)).toFloat()
        val y = cy + (radius * sin(angleRad)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
