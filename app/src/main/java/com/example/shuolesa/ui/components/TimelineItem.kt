package com.example.shuolesa.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.theme.BorderGray
import com.example.shuolesa.theme.DarkCard
import com.example.shuolesa.theme.NeonGreen
import com.example.shuolesa.theme.StatusFailed
import com.example.shuolesa.theme.StatusPending
import com.example.shuolesa.theme.StatusUploaded
import com.example.shuolesa.theme.TextMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Timeline list item showing audio recording info with status indicator.
 */
@Composable
fun TimelineItem(
    record: AudioRecordEntity,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val statusColor = when (record.status) {
        AudioRecordEntity.STATUS_UPLOADED -> StatusUploaded
        AudioRecordEntity.STATUS_PENDING, AudioRecordEntity.STATUS_UPLOADING -> StatusPending
        AudioRecordEntity.STATUS_FAILED -> StatusFailed
        else -> TextMuted
    }

    val statusText = when (record.status) {
        AudioRecordEntity.STATUS_UPLOADED -> "已上传"
        AudioRecordEntity.STATUS_PENDING -> "待上传"
        AudioRecordEntity.STATUS_UPLOADING -> "上传中..."
        AudioRecordEntity.STATUS_FAILED -> "失败"
        else -> record.status
    }

    val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    val timeStr = dateFormat.format(Date(record.createdAt))
    val durationStr = formatDuration(record.durationMs)
    val sizeStr = formatFileSize(record.fileSizeBytes)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Status indicator dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor),
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Info column
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
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "时长 $durationStr",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = sizeStr,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (record.chunkIndex > 0) {
                    Text(
                        text = "chunk #${record.chunkIndex} · ${record.sessionId}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes}m ${seconds}s"
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> String.format("%.1fMB", bytes / (1024.0 * 1024.0))
    }
}
