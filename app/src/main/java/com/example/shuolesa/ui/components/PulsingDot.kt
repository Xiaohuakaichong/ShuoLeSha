package com.example.shuolesa.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.example.shuolesa.theme.Accent
import com.example.shuolesa.theme.AppColor

/**
 * Breathing recording beacon. Color follows the current recording role.
 */
@Composable
fun PulsingDot(
    modifier: Modifier = Modifier,
    size: Int = 120,
    color: Color = Accent,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    val outerRingScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 2.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring",
    )

    val outerRingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringAlpha",
    )

    Canvas(modifier = modifier.size(size.dp)) {
        val center = Offset(this.size.width / 2, this.size.height / 2)
        val coreRadius = this.size.minDimension / 6

        // Outer expanding ring
        drawCircle(
            color = color.copy(alpha = outerRingAlpha),
            radius = coreRadius * outerRingScale,
            center = center,
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = glowAlpha),
                    color.copy(alpha = glowAlpha * 0.2f),
                    AppColor.background.copy(alpha = 0f),
                ),
                center = center,
                radius = coreRadius * 3 * scale,
            ),
            radius = coreRadius * 3 * scale,
            center = center,
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color, color.copy(alpha = 0.8f)),
                center = center,
                radius = coreRadius * scale,
            ),
            radius = coreRadius * scale,
            center = center,
        )
    }
}
