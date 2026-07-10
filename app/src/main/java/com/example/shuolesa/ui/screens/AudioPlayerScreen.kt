package com.example.shuolesa.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.shuolesa.audio.OpusPlayer
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.theme.BorderGray
import com.example.shuolesa.theme.DarkCard
import com.example.shuolesa.theme.DarkGray
import com.example.shuolesa.theme.MediumGray
import com.example.shuolesa.theme.NeonGreen
import com.example.shuolesa.theme.NeonGreenDim
import com.example.shuolesa.theme.PureBlack
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.theme.TextPrimary
import com.example.shuolesa.theme.TextSecondary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Audio playback screen with seekable timeline.
 * Supports play/pause, seek via slider, and 10s skip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(
    record: AudioRecordEntity,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(record.durationMs.coerceAtLeast(1L)) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }

    val opusPlayer = remember { OpusPlayer(context) }

    // Intercept system back gestures to exit player screen properly instead of closing activity
    BackHandler(enabled = true) {
        try {
            opusPlayer.stop()
        } catch (_: Exception) {}
        onBack()
    }

    // Initialize media player
    DisposableEffect(record.filePath) {
        opusPlayer.onProgressUpdate = { pos ->
            if (!isSeeking) {
                currentPositionMs = pos
                sliderPosition = (pos.toFloat() / durationMs).coerceIn(0f, 1f)
            }
        }
        opusPlayer.onCompletion = {
            isPlaying = false
            currentPositionMs = durationMs
            sliderPosition = 1f
        }
        opusPlayer.onError = { err ->
            playerError = err
        }

        val success = opusPlayer.prepare(record.filePath)
        if (success) {
            durationMs = opusPlayer.getDurationMs().coerceAtLeast(1L)
        }

        onDispose {
            opusPlayer.release()
        }
    }

    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val timeStr = dateFormat.format(Date(record.createdAt))

    // Rotating ring animation for playing state
    val infiniteTransition = rememberInfiniteTransition(label = "ring")
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
        ),
        label = "rotation",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            // Top bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
            ) {
                IconButton(onClick = {
                    try {
                        opusPlayer.stop()
                    } catch (_: Exception) { }
                    onBack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = TextPrimary,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "> 播放_",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NeonGreen,
                )
            }

            // Record info card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkCard)
                    .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
                    .padding(16.dp),
            ) {
                Column {
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonGreen,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "会话 ${record.sessionId} · chunk #${record.chunkIndex}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "状态: ${record.status} · ${formatFileSize(record.fileSizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }

            // AI Analysis Results Panel
            if (!record.transcription.isNullOrBlank() || !record.agentResult.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkCard)
                        .border(1.dp, NeonGreen.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                ) {
                    Text(
                        text = "> AGENT ANALYSIS_",
                        style = MaterialTheme.typography.labelMedium,
                        color = NeonGreen,
                    )

                    if (!record.transcription.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "识别文本 (TRANSCRIPTION):",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = record.transcription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                        )
                    }

                    if (!record.agentResult.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "处理结果 (AGENT OUTPUT):",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = record.agentResult,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeonGreenDim,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Visualization circle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
            ) {
                // Outer rotating ring (only when playing)
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .rotate(ringRotation)
                            .border(
                                width = 2.dp,
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        NeonGreen.copy(alpha = 0f),
                                        NeonGreen.copy(alpha = 0.6f),
                                        NeonGreen,
                                        NeonGreen.copy(alpha = 0f),
                                    ),
                                ),
                                shape = CircleShape,
                            ),
                    )
                }

                // Inner circle
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    if (isPlaying) NeonGreen.copy(alpha = 0.15f) else DarkGray,
                                    DarkCard,
                                ),
                            ),
                        )
                        .border(1.dp, if (isPlaying) NeonGreen.copy(alpha = 0.3f) else BorderGray, CircleShape),
                ) {
                    // Progress percentage
                    val percent = (sliderPosition * 100).toInt()
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.headlineLarge,
                            color = if (isPlaying) NeonGreen else TextMuted,
                        )
                        Text(
                            text = if (isPlaying) "PLAYING" else "PAUSED",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isPlaying) NeonGreenDim else TextMuted,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Error message
            if (playerError != null) {
                Text(
                    text = playerError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = com.example.shuolesa.theme.DangerRed,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )
            }

            // Time display
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            ) {
                Text(
                    text = formatTime(if (isSeeking) (sliderPosition * durationMs).toLong() else currentPositionMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = NeonGreen,
                )
                Text(
                    text = formatTime(durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }

            // Seekable slider
            Slider(
                value = sliderPosition,
                onValueChange = { value ->
                    isSeeking = true
                    sliderPosition = value
                },
                onValueChangeFinished = {
                    val seekTo = (sliderPosition * durationMs).toLong()
                    try {
                        opusPlayer.seekTo(seekTo)
                        currentPositionMs = seekTo
                    } catch (_: Exception) { }
                    isSeeking = false
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = NeonGreen,
                    activeTrackColor = NeonGreen,
                    inactiveTrackColor = MediumGray,
                ),
                enabled = playerError == null,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Playback controls
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
            ) {
                // Rewind 10s
                IconButton(
                    onClick = {
                        try {
                            val newPos = (currentPositionMs - 10000).coerceAtLeast(0)
                            opusPlayer.seekTo(newPos)
                            currentPositionMs = newPos
                            sliderPosition = (newPos.toFloat() / durationMs).coerceIn(0f, 1f)
                        } catch (_: Exception) { }
                    },
                    enabled = playerError == null,
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "后退10秒",
                        tint = if (playerError == null) TextPrimary else TextMuted,
                        modifier = Modifier.size(32.dp),
                    )
                }

                // Play/Pause button
                IconButton(
                    onClick = {
                        if (playerError != null) return@IconButton
                        try {
                            if (isPlaying) {
                                opusPlayer.pause()
                                isPlaying = false
                            } else {
                                // If at end, restart
                                if (currentPositionMs >= durationMs - 100) {
                                    opusPlayer.seekTo(0)
                                    currentPositionMs = 0
                                    sliderPosition = 0f
                                }
                                opusPlayer.start()
                                isPlaying = true
                            }
                        } catch (_: Exception) { }
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPlaying) NeonGreen.copy(alpha = 0.15f) else NeonGreen.copy(alpha = 0.1f),
                        )
                        .border(2.dp, NeonGreen, CircleShape),
                    enabled = playerError == null,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = NeonGreen,
                        modifier = Modifier.size(40.dp),
                    )
                }

                // Forward 10s
                IconButton(
                    onClick = {
                        try {
                            val newPos = (currentPositionMs + 10000).coerceAtMost(durationMs)
                            opusPlayer.seekTo(newPos)
                            currentPositionMs = newPos
                            sliderPosition = (newPos.toFloat() / durationMs).coerceIn(0f, 1f)
                        } catch (_: Exception) { }
                    },
                    enabled = playerError == null,
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "快进10秒",
                        tint = if (playerError == null) TextPrimary else TextMuted,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> String.format("%.1fMB", bytes / (1024.0 * 1024.0))
    }
}
