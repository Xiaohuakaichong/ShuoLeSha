package com.example.shuolesa.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.data.prefs.AppPreferences
import com.example.shuolesa.data.repository.AudioRepository
import com.example.shuolesa.theme.Dimens
import com.example.shuolesa.theme.PureBlack
import com.example.shuolesa.theme.TextSecondary
import com.example.shuolesa.ui.components.EmptyState
import com.example.shuolesa.ui.components.PageHeader
import com.example.shuolesa.ui.components.TimelineItem

/**
 * 记忆时间线：待处理与已上传录音。
 */
@Composable
fun TimelineScreen(
    repository: AudioRepository,
    prefs: AppPreferences,
    onRecordClick: (AudioRecordEntity) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val allRecords by repository.observeAllRecords().collectAsState(initial = emptyList())
    val triggerDuration by prefs.triggerDuration.collectAsState(initial = 3)

    val pendingRecords = allRecords.filter {
        it.status == AudioRecordEntity.STATUS_PENDING ||
            it.status == AudioRecordEntity.STATUS_UPLOADING ||
            it.status == AudioRecordEntity.STATUS_FAILED
    }
    val uploadedRecords = allRecords.filter { it.status == AudioRecordEntity.STATUS_UPLOADED }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(horizontal = Dimens.pagePaddingH, vertical = Dimens.pagePaddingV),
    ) {
        PageHeader(
            title = "记忆时间线",
            subtitle = "录音切片与上传状态",
        )

        Spacer(modifier = Modifier.height(Dimens.gapLg))

        if (allRecords.isEmpty()) {
            EmptyTimeline(triggerSeconds = triggerDuration)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Dimens.gapSm),
                contentPadding = PaddingValues(bottom = Dimens.pageBottomNavClearance),
            ) {
                if (pendingRecords.isNotEmpty()) {
                    item {
                        SectionHeader("待处理 · ${pendingRecords.size}")
                    }
                    items(pendingRecords, key = { it.id }) { record ->
                        TimelineItem(record = record, onClick = { onRecordClick(record) })
                    }
                    item { Spacer(modifier = Modifier.height(Dimens.gapMd)) }
                }

                if (uploadedRecords.isNotEmpty()) {
                    item {
                        SectionHeader("已上传 · ${uploadedRecords.size}")
                    }
                    items(uploadedRecords, key = { it.id }) { record ->
                        TimelineItem(record = record, onClick = { onRecordClick(record) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary,
        modifier = Modifier.padding(vertical = Dimens.gapXs),
    )
}

@Composable
private fun EmptyTimeline(triggerSeconds: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "empty")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "emptyAlpha",
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        EmptyState(
            symbol = "[ ]",
            title = "尚无录音记录",
            subtitle = "同时长按音量 +/- ${triggerSeconds} 秒开始录音",
            modifier = Modifier.alpha(alpha),
        )
    }
}
