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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shuolesa.theme.CardDark
import com.example.shuolesa.theme.CardElevated
import com.example.shuolesa.theme.DarkSurface
import com.example.shuolesa.theme.Dimens
import com.example.shuolesa.theme.MintCyan
import com.example.shuolesa.theme.NeonGreen
import com.example.shuolesa.theme.SurfaceBorder
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.theme.TextPrimary
import com.example.shuolesa.theme.TextSecondary

/**
 * 录音启动模式选择弹窗：供用户在点击“开始录制”时选择 LifeLog 还是 会议模式。
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
        containerColor = DarkSurface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.pagePaddingH, vertical = Dimens.gapMd),
        ) {
            // Header
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
                        text = "请根据当前录音场景选择对应的品质格式",
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

            Spacer(modifier = Modifier.height(16.dp))

            // Option 1: LifeLog
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardDark)
                    .border(1.dp, NeonGreen.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .clickable { onSelectMode("lifelog") }
                    .padding(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(NeonGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = NeonGreen,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "🌿 LifeLog 随身闲聊",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NeonGreen.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "小文件 · 极省空间",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "硬件 AAC 压缩 · 约 7~10MB/小时 · 秒级上传，适合日常碎碎念、散步闲聊与个人手记。",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Option 2: Meeting
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardDark)
                    .border(1.dp, MintCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .clickable { onSelectMode("meeting") }
                    .padding(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MintCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkOutline,
                            contentDescription = null,
                            tint = MintCyan,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "💼 高保真会议模式",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MintCyan.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "大文件 · 无损清晰",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MintCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "无损 WAV / 高清 AAC · 完整保留微弱对白与多人声纹，适合重要商务谈判、团队例会与访谈。",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
