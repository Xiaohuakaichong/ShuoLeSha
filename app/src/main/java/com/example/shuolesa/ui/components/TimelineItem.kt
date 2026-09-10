package com.example.shuolesa.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.theme.CardElevated
import com.example.shuolesa.theme.Dimens
import com.example.shuolesa.theme.ElectricBlue
import com.example.shuolesa.theme.MintCyan
import com.example.shuolesa.theme.SurfaceBorder
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.theme.TextPrimary
import com.example.shuolesa.theme.TextSecondary
import com.example.shuolesa.util.Formatters
import org.json.JSONArray

/**
 * 现代智能便签卡片：展示时间、时长、AI 总结、待办事项、标签及转写展开。
 */
@Composable
fun TimelineItem(
    record: AudioRecordEntity,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val timeStr = Formatters.formatListDate(record.createdAt)
    val durationStr = Formatters.formatDuration(record.durationMs)
    var expandedTranscription by remember { mutableStateOf(false) }

    // Parse action items & tags from JSON if available
    val actionItems = remember(record.actionItems) {
        val list = mutableListOf<String>()
        if (!record.actionItems.isNullOrBlank()) {
            try {
                val json = JSONArray(record.actionItems)
                for (i in 0 until json.length()) {
                    list.add(json.getString(i))
                }
            } catch (_: Exception) {
                record.actionItems.lines().filter { it.isNotBlank() }.forEach { list.add(it) }
            }
        }
        list
    }

    val tags = remember(record.tags) {
        val list = mutableListOf<String>()
        if (!record.tags.isNullOrBlank()) {
            try {
                val json = JSONArray(record.tags)
                for (i in 0 until json.length()) {
                    list.add(json.getString(i))
                }
            } catch (_: Exception) {
                record.tags.split(",", " ").filter { it.isNotBlank() }.forEach { list.add(it) }
            }
        }
        list
    }

    TerminalCard(
        modifier = modifier.clickable(onClick = onClick),
        borderColor = SurfaceBorder,
    ) {
        // Top Header: Time, Duration pill, Status Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CardElevated)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "⏱️ $durationStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 11.sp,
                    )
                }
            }
            StatusBadge(status = record.status)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Title
        val titleText = record.title?.takeIf { it.isNotBlank() } ?: "随手语音记录 #${record.id}"
        Text(
            text = titleText,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // Summary box
        val summaryText = record.summary?.takeIf { it.isNotBlank() }
            ?: record.transcription?.takeIf { it.isNotBlank() }

        if (!summaryText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardElevated)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    lineHeight = 20.sp,
                    maxLines = if (expandedTranscription) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Action Items checklist
        if (actionItems.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "待办事项 (${actionItems.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MintCyan,
                    fontWeight = FontWeight.SemiBold,
                )
                actionItems.take(4).forEach { item ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MintCyan,
                            modifier = Modifier
                                .size(15.dp)
                                .padding(top = 2.dp),
                        )
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Tags & Expand Section
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Tags list
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                if (tags.isNotEmpty()) {
                    tags.take(3).forEach { tag ->
                        TagChip(tag = tag)
                    }
                } else {
                    TagChip(tag = "随手记")
                }
            }

            // Quick Play Button & Expand transcription
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (!record.transcription.isNullOrBlank()) {
                    IconButton(
                        onClick = { expandedTranscription = !expandedTranscription },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = if (expandedTranscription) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "展开文字稿",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                IconButton(
                    onClick = onClick,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MintCyan.copy(alpha = 0.15f)),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "播放录音",
                        tint = MintCyan,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // Expandable Raw Transcription
        AnimatedVisibility(visible = expandedTranscription && !record.transcription.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            ) {
                Text(
                    text = "逐字识别文本：",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = record.transcription ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}
