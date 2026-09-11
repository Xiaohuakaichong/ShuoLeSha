package com.example.shuolesa.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.shuolesa.theme.CardDark
import com.example.shuolesa.theme.CardElevated
import com.example.shuolesa.theme.Dimens
import com.example.shuolesa.theme.ModeCasual
import com.example.shuolesa.theme.ModeMeeting
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.theme.TextPrimary
import com.example.shuolesa.ui.components.pressScale

/**
 * 录音启动模式选择：随身 / 会议。底层协议值仍是 lifelog / meeting。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingModeSelectSheet(
    onSelectMode: (mode: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardElevated,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.pagePaddingH, vertical = Dimens.gapMd),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "选择录音模式",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "随身省流，会议高保真。可在设置里改默认策略。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = TextMuted,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.gapMd))

            ModeOptionCard(
                title = "随身",
                badge = "小文件",
                description = "硬件 AAC 压缩，约 7–10MB/小时。适合碎碎念、散步和日常手记。",
                icon = Icons.Outlined.GraphicEq,
                accent = ModeCasual,
                onClick = { onSelectMode("lifelog") },
            )

            Spacer(modifier = Modifier.height(Dimens.gapSm))

            ModeOptionCard(
                title = "会议",
                badge = "高保真",
                description = "无损 WAV / 高清 AAC，保留微弱对白与多人声纹。适合例会、谈判和访谈。",
                icon = Icons.Default.WorkOutline,
                accent = ModeMeeting,
                onClick = { onSelectMode("meeting") },
            )

            Spacer(modifier = Modifier.height(Dimens.gapLg))
        }
    }
}

@Composable
private fun ModeOptionCard(
    title: String,
    badge: String,
    description: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(Dimens.cardRadius)
    Box(
        modifier = Modifier
            .pressScale(pressed, 0.985f)
            .fillMaxWidth()
            .clip(shape)
            .background(CardDark)
            .border(Dimens.borderThin, accent.copy(alpha = 0.4f), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(Dimens.cardPadding),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.gapSm),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimens.chipRadius))
                            .background(accent.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Dimens.gapXs))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
        }
    }
}
