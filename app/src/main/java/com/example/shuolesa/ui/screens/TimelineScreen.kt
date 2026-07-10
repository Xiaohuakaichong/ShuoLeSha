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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.shuolesa.data.db.AudioRecordEntity
import com.example.shuolesa.data.repository.AudioRepository
import com.example.shuolesa.theme.NeonGreen
import com.example.shuolesa.theme.PureBlack
import com.example.shuolesa.theme.TextMuted
import com.example.shuolesa.theme.TextSecondary
import com.example.shuolesa.ui.components.TimelineItem

/**
 * Page 2: Memory Timeline — shows pending and uploaded audio records.
 */
@Composable
fun TimelineScreen(
    repository: AudioRepository,
    onRecordClick: (AudioRecordEntity) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val allRecords by repository.observeAllRecords().collectAsState(initial = emptyList())

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
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        // Title
        Text(
            text = "> 记忆时间线_",
            style = MaterialTheme.typography.headlineLarge,
            color = NeonGreen,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "MEMORY TIMELINE",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (allRecords.isEmpty()) {
            // Empty state
            EmptyTimeline()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                // Pending section
                if (pendingRecords.isNotEmpty()) {
                    item {
                        SectionHeader("待处理 · ${pendingRecords.size}")
                    }
                    items(pendingRecords, key = { it.id }) { record ->
                        TimelineItem(record = record, onClick = { onRecordClick(record) })
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }

                // Uploaded section
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
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun EmptyTimeline() {
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha),
        ) {
            Text(
                text = "[ ]",
                style = MaterialTheme.typography.displayLarge,
                color = TextMuted,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "尚无录音记录",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "长按音量+/- 3秒开始录音",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}
