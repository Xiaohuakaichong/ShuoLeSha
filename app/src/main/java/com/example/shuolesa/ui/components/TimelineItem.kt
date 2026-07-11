package com.example.shuolesa.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.theme.Dimens
import com.example.shuolesa.theme.NeonGreen
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.theme.TextPrimary
import com.example.shuolesa.util.Formatters

/**
 * 时间线条目：时间、状态、时长与体积。
 */
@Composable
fun TimelineItem(
    record: AudioRecordEntity,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val statusColor = statusColor(record.status)
    val timeStr = Formatters.formatListDate(record.createdAt)
    val durationStr = Formatters.formatDuration(record.durationMs)
    val sizeStr = Formatters.formatFileSize(record.fileSizeBytes)

    TerminalCard(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .size(Dimens.statusDotLg)
                    .clip(CircleShape)
                    .background(statusColor),
            )

            Spacer(modifier = Modifier.width(Dimens.gapMd))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonGreen,
                    )
                    Text(
                        text = statusLabel(record.status),
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                    )
                }

                Spacer(modifier = Modifier.height(Dimens.gapXs))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "时长 $durationStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                    Text(
                        text = sizeStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }

                if (record.chunkIndex > 0 || !record.transcription.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(Dimens.gapXs))
                    val meta = buildString {
                        if (record.chunkIndex > 0) {
                            append("分片 #${record.chunkIndex}")
                        }
                        if (!record.transcription.isNullOrBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append("含转写")
                        }
                    }
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
