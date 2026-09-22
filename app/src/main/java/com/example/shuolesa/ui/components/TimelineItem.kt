package com.example.shuolesa.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.data.model.ActionItemModel
import com.example.shuolesa.theme.Dimens
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.theme.TextPrimary
import com.example.shuolesa.theme.TextSecondary
import com.example.shuolesa.theme.recordRoleColor
import com.example.shuolesa.util.Formatters

/**
 * Spine timeline: time + role dot on the left, journal card on the right.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimelineItem(
    record: AudioRecordEntity,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val durationStr = Formatters.formatDuration(record.durationMs)
    val actionItems = remember(record.actionItems) {
        ActionItemModel.fromJsonString(record.actionItems)
    }
    val isDigest = record.isDailyLifeLogSummary()
    val isMeeting = record.isMeeting()
    val accent = recordRoleColor(isDigest, isMeeting)
    val pendingCount = actionItems.count { !it.isDone }
    val titleText = record.title?.takeIf { it.isNotBlank() }
        ?: if (isMeeting) "会议录音" else "随身记录"
    val summaryText = record.summary?.takeIf { it.isNotBlank() }
        ?: record.transcription?.takeIf { it.isNotBlank() }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(pressed, 0.985f)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Column(
            modifier = Modifier
                .width(52.dp)
                .padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = Formatters.formatTimeOnly(record.createdAt),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(72.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.28f)),
            )
        }

        TerminalCard(
            modifier = Modifier.weight(1f),
            borderColor = accent.copy(alpha = 0.18f),
            backgroundColor = com.example.shuolesa.theme.CardDark,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
                ) {
                    ModeBadge(isMeeting = isMeeting, isDigest = isDigest)
                    if (!isDigest) {
                        Text(
                            text = durationStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                        )
                    }
                }
                StatusBadge(status = record.status)
            }

            Spacer(modifier = Modifier.height(Dimens.gapSm))

            Text(
                text = titleText,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (!summaryText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Dimens.gapXs))
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (actionItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Dimens.gapSm))
                Text(
                    text = if (pendingCount > 0) "$pendingCount 项待办未完成" else "待办已全部完成",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (pendingCount > 0) accent else TextMuted,
                )
            }
        }
    }
}
