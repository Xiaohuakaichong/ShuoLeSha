package com.example.shuolesa.ui.screens

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.shuolesa.service.AudioCaptureService
import com.example.shuolesa.theme.DangerRed
import com.example.shuolesa.theme.DangerRedDim
import com.example.shuolesa.theme.NeonGreen
import com.example.shuolesa.theme.PureBlack
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.ui.components.PulsingDot
import kotlinx.coroutines.delay

/**
 * Page 3: Active Recording overlay — shown when recording is in progress.
 */
@Composable
fun ActiveRecordingScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var elapsedMs by remember { mutableLongStateOf(0L) }

    // Poll elapsed time
    LaunchedEffect(Unit) {
        while (true) {
            elapsedMs = AudioCaptureService.elapsedMs
            delay(200)
        }
    }

    val elapsedStr = formatElapsed(elapsedMs)

    // Force Stop button with press scale
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = tween(100),
        label = "buttonScale",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Recording label
            Text(
                text = "REC",
                style = MaterialTheme.typography.labelLarge,
                color = NeonGreen,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Pulsing dot
            PulsingDot(size = 180)

            Spacer(modifier = Modifier.height(32.dp))

            // Elapsed time
            Text(
                text = elapsedStr,
                style = MaterialTheme.typography.displayLarge,
                color = NeonGreen,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "recording...",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )

            Spacer(modifier = Modifier.height(64.dp))

            // Force Stop button
            Button(
                onClick = {
                    val intent = Intent(context, AudioCaptureService::class.java).apply {
                        action = AudioCaptureService.ACTION_STOP
                    }
                    context.startService(intent)
                },
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(56.dp)
                    .scale(buttonScale),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DangerRed.copy(alpha = 0.2f),
                    contentColor = DangerRed,
                ),
                shape = RoundedCornerShape(12.dp),
                interactionSource = interactionSource,
            ) {
                Text(
                    text = "■  FORCE STOP",
                    style = MaterialTheme.typography.labelLarge,
                    color = DangerRed,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
