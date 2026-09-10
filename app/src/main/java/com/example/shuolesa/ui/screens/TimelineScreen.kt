package com.example.shuolesa.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.data.prefs.AppPreferences
import com.example.shuolesa.data.prefs.ProviderPreset
import com.example.shuolesa.data.repository.AudioRepository
import com.example.shuolesa.theme.BgDark
import com.example.shuolesa.theme.CardDark
import com.example.shuolesa.theme.CardElevated
import com.example.shuolesa.theme.Dimens
import com.example.shuolesa.theme.ElectricBlue
import com.example.shuolesa.theme.MintCyan
import com.example.shuolesa.theme.SurfaceBorder
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.theme.TextPrimary
import com.example.shuolesa.theme.TextSecondary
import com.example.shuolesa.ui.components.EmptyState
import com.example.shuolesa.ui.components.PageHeader
import com.example.shuolesa.ui.components.TimelineItem

/**
 * 现代记忆流时间线：智能便签流与分类筛选。
 */
@Composable
fun TimelineScreen(
    repository: AudioRepository,
    prefs: AppPreferences,
    onRecordClick: (AudioRecordEntity) -> Unit = {},
    onStartRecord: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val allRecords by repository.observeAllRecords().collectAsState(initial = emptyList())
    val triggerDuration by prefs.triggerDuration.collectAsState(initial = 3)
    val providerMode by prefs.providerMode.collectAsState(initial = ProviderPreset.STEPFUN.id)

    var selectedFilter by remember { mutableStateOf("全部") }
    val filterTabs = listOf("全部", "已提炼", "含待办", "处理中")

    val providerName = remember(providerMode) {
        when (providerMode) {
            ProviderPreset.STEPFUN.id -> "StepFun 驱动"
            ProviderPreset.SILICONFLOW.id -> "SiliconFlow"
            else -> "本地/自定义"
        }
    }

    val filteredRecords = remember(allRecords, selectedFilter) {
        when (selectedFilter) {
            "已提炼" -> allRecords.filter { it.status == AudioRecordEntity.STATUS_UPLOADED && !it.summary.isNullOrBlank() }
            "含待办" -> allRecords.filter { !it.actionItems.isNullOrBlank() && it.actionItems != "[]" }
            "处理中" -> allRecords.filter { it.status == AudioRecordEntity.STATUS_PENDING || it.status == AudioRecordEntity.STATUS_UPLOADING }
            else -> allRecords
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = Dimens.pagePaddingH, vertical = Dimens.pagePaddingV),
    ) {
        PageHeader(
            title = "说了啥 · 记忆流",
            subtitle = "随手语音转写与 AI 智能提炼",
            trailing = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardElevated)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = providerName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MintCyan,
                        fontWeight = FontWeight.Medium,
                    )
                }
            },
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filter chips row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(filterTabs) { tab ->
                val isSelected = selectedFilter == tab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MintCyan.copy(alpha = 0.15f) else CardDark)
                        .clickable { selectedFilter = tab }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = tab,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) MintCyan else TextSecondary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (filteredRecords.isEmpty()) {
            EmptyTimeline(
                triggerSeconds = triggerDuration,
                onStartRecord = onStartRecord,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = Dimens.pageBottomNavClearance),
            ) {
                items(filteredRecords, key = { it.id }) { record ->
                    TimelineItem(
                        record = record,
                        onClick = { onRecordClick(record) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTimeline(
    triggerSeconds: Int,
    onStartRecord: () -> Unit = {},
) {
    val infiniteTransition = rememberInfiniteTransition(label = "empty")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "emptyAlpha",
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(
            symbol = "🎙️",
            title = "尚无录音记录",
            subtitle = "点击右下角悬浮按钮开启录音\n或在后台/息屏长按音量 +/- 键 ${triggerSeconds} 秒盲操录音",
            modifier = Modifier.alpha(alpha),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(MintCyan.copy(alpha = 0.12f))
                .clickable { onStartRecord() }
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text(
                text = "⚡ 立即启动录音",
                style = MaterialTheme.typography.labelLarge,
                color = MintCyan,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
