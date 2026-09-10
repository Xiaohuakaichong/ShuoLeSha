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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shuolesa.audio.OpusPlayer
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.theme.DangerRed
import com.example.shuolesa.theme.DarkCard
import com.example.shuolesa.theme.DarkGray
import com.example.shuolesa.theme.Dimens
import com.example.shuolesa.theme.MintCyan
import com.example.shuolesa.theme.NeonGreen
import com.example.shuolesa.theme.NeonGreenDim
import com.example.shuolesa.theme.PureBlack
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.theme.TextPrimary
import com.example.shuolesa.theme.TextSecondary
import com.example.shuolesa.ui.components.PageHeader
import com.example.shuolesa.ui.components.SectionLabel
import com.example.shuolesa.ui.components.StatusBadge
import com.example.shuolesa.ui.components.TerminalCard
import com.example.shuolesa.util.Formatters
import java.io.File

/**
 * 播放页：本地 Opus 回放 + 分析结果。
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
    val fileExists = remember(record.filePath) { File(record.filePath).exists() }
    var playerError by remember(record.filePath) {
        mutableStateOf(
            if (!fileExists) "本地文件已清理（上传成功后仅保留分析结果）" else null,
        )
    }

    val opusPlayer = remember { OpusPlayer(context) }

    BackHandler(enabled = true) {
        try {
            opusPlayer.stop()
        } catch (_: Exception) {
        }
        onBack()
    }

    DisposableEffect(record.filePath) {
        if (!fileExists) {
            onDispose { }
        } else {
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
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ring")
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
        ),
        label = "rotation",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Dimens.pagePaddingH),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.gapSm, bottom = Dimens.gapMd),
        ) {
            IconButton(onClick = {
                try {
                    opusPlayer.stop()
                } catch (_: Exception) {
                }
                onBack()
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = TextPrimary,
                )
            }
            Spacer(modifier = Modifier.width(Dimens.gapXs))
            PageHeader(
                title = "播放",
                subtitle = "回放与分析结果",
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            TerminalCard {
                Text(
                    text = Formatters.formatDetailDate(record.createdAt),
                    style = MaterialTheme.typography.titleMedium,
                    color = MintCyan,
                )
                Spacer(modifier = Modifier.height(Dimens.gapXs))
                Text(
                    text = "会话 ${record.sessionId} · 分片 #${record.chunkIndex}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(Dimens.gapSm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusBadge(status = record.status)
                    Text(
                        text = Formatters.formatFileSize(record.fileSizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }

            if (!record.title.isNullOrBlank() || !record.summary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Dimens.gapMd))
                TerminalCard {
                    record.title?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(Dimens.gapSm))
                    }
                    record.summary?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            lineHeight = 22.sp,
                        )
                    }
                }
            }

            if (!record.transcription.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Dimens.gapMd))
                TerminalCard {
                    SectionLabel("逐字转录稿")
                    Spacer(modifier = Modifier.height(Dimens.gapSm))
                    Text(
                        text = record.transcription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        lineHeight = 21.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.gapLg))

            // Visualization
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.gapMd),
            ) {
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .rotate(ringRotation)
                            .border(
                                width = Dimens.borderThick,
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
                        .border(
                            Dimens.borderThin,
                            if (isPlaying) NeonGreen.copy(alpha = 0.3f) else com.example.shuolesa.theme.BorderGray,
                            CircleShape,
                        ),
                ) {
                    val percent = (sliderPosition * 100).toInt()
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.headlineLarge,
                            color = if (isPlaying) NeonGreen else TextMuted,
                        )
                        Text(
                            text = when {
                                playerError != null -> "不可用"
                                isPlaying -> "播放中"
                                else -> "已暂停"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isPlaying) NeonGreenDim else TextMuted,
                        )
                    }
                }
            }

            if (playerError != null) {
                Text(
                    text = playerError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = DangerRed,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Dimens.gapMd),
                )
            }
        }

        // Controls (pinned bottom)
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.gapXs),
        ) {
            Text(
                text = Formatters.formatElapsed(
                    if (isSeeking) (sliderPosition * durationMs).toLong() else currentPositionMs,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = NeonGreen,
            )
            Text(
                text = Formatters.formatElapsed(durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }

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
                } catch (_: Exception) {
                }
                isSeeking = false
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = NeonGreen,
                activeTrackColor = NeonGreen,
                inactiveTrackColor = DarkGray,
            ),
            enabled = playerError == null,
        )

        Spacer(modifier = Modifier.height(Dimens.gapMd))

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Dimens.gapXl),
        ) {
            IconButton(
                onClick = {
                    try {
                        val newPos = (currentPositionMs - 10000).coerceAtLeast(0)
                        opusPlayer.seekTo(newPos)
                        currentPositionMs = newPos
                        sliderPosition = (newPos.toFloat() / durationMs).coerceIn(0f, 1f)
                    } catch (_: Exception) {
                    }
                },
                enabled = playerError == null,
            ) {
                Icon(
                    imageVector = Icons.Default.Replay10,
                    contentDescription = "后退 10 秒",
                    tint = if (playerError == null) TextPrimary else TextMuted,
                    modifier = Modifier.size(32.dp),
                )
            }

            IconButton(
                onClick = {
                    if (playerError != null) return@IconButton
                    try {
                        if (isPlaying) {
                            opusPlayer.pause()
                            isPlaying = false
                        } else {
                            if (currentPositionMs >= durationMs - 100) {
                                opusPlayer.seekTo(0)
                                currentPositionMs = 0
                                sliderPosition = 0f
                            }
                            opusPlayer.start()
                            isPlaying = true
                        }
                    } catch (_: Exception) {
                    }
                },
                modifier = Modifier
                    .size(Dimens.iconButtonLg)
                    .clip(CircleShape)
                    .background(
                        if (isPlaying) NeonGreen.copy(alpha = 0.15f) else NeonGreen.copy(alpha = 0.1f),
                    )
                    .border(Dimens.borderThick, NeonGreen, CircleShape),
                enabled = playerError == null,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = NeonGreen,
                    modifier = Modifier.size(40.dp),
                )
            }

            IconButton(
                onClick = {
                    try {
                        val newPos = (currentPositionMs + 10000).coerceAtMost(durationMs)
                        opusPlayer.seekTo(newPos)
                        currentPositionMs = newPos
                        sliderPosition = (newPos.toFloat() / durationMs).coerceIn(0f, 1f)
                    } catch (_: Exception) {
                    }
                },
                enabled = playerError == null,
            ) {
                Icon(
                    imageVector = Icons.Default.Forward10,
                    contentDescription = "快进 10 秒",
                    tint = if (playerError == null) TextPrimary else TextMuted,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}
